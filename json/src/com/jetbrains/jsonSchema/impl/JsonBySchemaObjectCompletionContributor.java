package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.DataManager;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Irina.Chernushina on 10/1/2015.
 */
class JsonBySchemaObjectCompletionContributor extends CompletionContributor {
  private static final String BUILTIN_USAGE_KEY = "json.schema.builtin.completion";
  private static final String SCHEMA_USAGE_KEY = "json.schema.schema.completion";
  private static final String USER_USAGE_KEY = "json.schema.user.completion";
  @NotNull private final SchemaType myType;
  @NotNull private final VirtualFile mySchemaFile;
  @NotNull private final JsonSchemaObject myRootSchema;

  public JsonBySchemaObjectCompletionContributor(@NotNull SchemaType type,
                                                 @NotNull VirtualFile schemaFile,
                                                 final @NotNull JsonSchemaObject rootSchema) {
    myType = type;
    mySchemaFile = schemaFile;
    myRootSchema = rootSchema;
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    final PsiFile containingFile = position.getContainingFile();
    if (containingFile == null) return;

    updateStat();
    final PsiElement completionPosition = parameters.getOriginalPosition() != null ? parameters.getOriginalPosition():
                                          parameters.getPosition();
    new Worker(myRootSchema, mySchemaFile, position, completionPosition, result).work();
    result.stopHere();
  }

  public static List<LookupElement> getCompletionVariants(@NotNull final JsonSchemaObject schema,
                                                          @NotNull final PsiElement position, @NotNull final PsiElement originalPosition,
                                                          @NotNull VirtualFile schemaFile) {
    final List<LookupElement> result = new ArrayList<>();
    new Worker(schema, schemaFile, position, originalPosition, element -> result.add(element)).work();
    return result;
  }

  private void updateStat() {
    if (SchemaType.schema.equals(myType)) {
      UsageTrigger.trigger(SCHEMA_USAGE_KEY);
    } else if (SchemaType.embeddedSchema.equals(myType)) {
      UsageTrigger.trigger(BUILTIN_USAGE_KEY);
    } else if (SchemaType.userSchema.equals(myType)) {
      UsageTrigger.trigger(USER_USAGE_KEY);
    }
  }

  private static class Worker {
    @NotNull private final JsonSchemaObject myRootSchema;
    @NotNull private final VirtualFile mySchemaFile;
    @NotNull private final PsiElement myPosition;
    @NotNull private final PsiElement myOriginalPosition;
    @NotNull private final Consumer<LookupElement> myResultConsumer;
    private final boolean myWrapInQuotes;
    private final boolean myInsideStringLiteral;
    private final List<LookupElement> myVariants;
    private JsonLikePsiWalker myWalker;

    public Worker(@NotNull JsonSchemaObject rootSchema, @NotNull final VirtualFile schemaFile, @NotNull PsiElement position,
                  @NotNull PsiElement originalPosition, @NotNull final Consumer<LookupElement> resultConsumer) {
      myRootSchema = rootSchema;
      mySchemaFile = schemaFile;
      myPosition = position;
      myOriginalPosition = originalPosition;
      myResultConsumer = resultConsumer;
      myVariants = new ArrayList<>();
      myWalker = JsonSchemaWalker.getWalker(myPosition, myRootSchema);
      myWrapInQuotes = myWalker.isNameQuoted() && !(position.getParent() instanceof JsonStringLiteral);
      myInsideStringLiteral = position.getParent() instanceof JsonStringLiteral;
    }

    public void work() {
      if (myWalker == null) return;
      JsonSchemaWalker.findSchemasForCompletion(myPosition, myWalker, new JsonSchemaWalker.CompletionSchemesConsumer() {
        @Override
        public void consume(boolean isName,
                            @NotNull JsonSchemaObject schema,
                            @NotNull VirtualFile schemaFile,
                            @NotNull List<JsonSchemaWalker.Step> steps) {
          if (isName) {
            final boolean insertComma = myWalker.hasPropertiesBehindAndNoComma(myPosition);
            final boolean hasValue = myWalker.isPropertyWithValue(myPosition.getParent().getParent());

            final Collection<String> properties = myWalker.getPropertyNamesOfParentObject(myOriginalPosition);
            final JsonPropertyAdapter adapter = myWalker.getParentPropertyAdapter(myOriginalPosition);

            JsonSchemaPropertyProcessor.process(new JsonSchemaPropertyProcessor.PropertyProcessor() {
              @Override
              public boolean process(String name, JsonSchemaObject schema) {
                if (properties.contains(name) && (adapter == null || !name.equals(adapter.getName()))) {
                  return true;
                }

                addPropertyVariant(name, schema, hasValue, insertComma);
                return true;
              }
            }, schema);
          }
          else {
            suggestValues(schema);
          }
        }

        @Override
        public void oneOf(boolean isName,
                          @NotNull List<JsonSchemaObject> list,
                          @NotNull VirtualFile schemaFile,
                          @NotNull List<JsonSchemaWalker.Step> steps) {
          list.forEach(s -> consume(isName, s, schemaFile, steps));
        }

        @Override
        public void anyOf(boolean isName,
                          @NotNull List<JsonSchemaObject> list,
                          @NotNull VirtualFile schemaFile,
                          @NotNull List<JsonSchemaWalker.Step> steps) {
          list.forEach(s -> consume(isName, s, schemaFile, steps));
        }
      }, myRootSchema, mySchemaFile);
      for (LookupElement variant : myVariants) {
        myResultConsumer.consume(variant);
      }
    }

    private void suggestValues(JsonSchemaObject schema) {
      suggestValuesForSchemaVariants(schema.getAnyOf());
      suggestValuesForSchemaVariants(schema.getOneOf());
      suggestValuesForSchemaVariants(schema.getAllOf());

      if (schema.getEnum() != null) {
        //myVariants.clear();
        for (Object o : schema.getEnum()) {
          addValueVariant(o.toString(), null);
        }
      }
      else {
        final JsonSchemaType type = schema.getType();
        if (type != null) {
          suggestByType(schema, type);
        } else if (schema.getTypeVariants() != null) {
          for (JsonSchemaType schemaType : schema.getTypeVariants()) {
            suggestByType(schema, schemaType);
          }
        }
      }
    }

    private void suggestByType(JsonSchemaObject schema, JsonSchemaType type) {
      if (JsonSchemaType._boolean.equals(type)) {
        addPossibleBooleanValue(type);
      } else if (JsonSchemaType._string.equals(type)) {
        addPossibleStringValue(schema);
      } else if (JsonSchemaType._null.equals(type)) {
        addValueVariant("null", null);
      }
    }

    private void addPossibleStringValue(JsonSchemaObject schema) {
      Object defaultValue = schema.getDefault();
      String defaultValueString = defaultValue == null ? null : defaultValue.toString();
      if (!StringUtil.isEmpty(defaultValueString)) {
        String quotedValue = defaultValueString;
        if (!StringUtil.isQuotedString(quotedValue)) {
          quotedValue = StringUtil.wrapWithDoubleQuote(quotedValue);
        }
        addValueVariant(quotedValue, null);
      }
    }

    private void suggestValuesForSchemaVariants(List<JsonSchemaObject> list) {
      if (list != null && list.size() > 0) {
        for (JsonSchemaObject schemaObject : list) {
          suggestValues(schemaObject);
        }
      }
    }

    private void addPossibleBooleanValue(JsonSchemaType type) {
      if (JsonSchemaType._boolean.equals(type)) {
        addValueVariant("true", null);
        addValueVariant("false", null);
      }
    }


    private void addValueVariant(@NotNull String key, @SuppressWarnings("SameParameterValue") @Nullable final String description) {
      LookupElementBuilder builder = LookupElementBuilder.create(!myWrapInQuotes ? StringUtil.unquoteString(key) : key);
      if (description != null) {
        builder = builder.withTypeText(description);
      }
      myVariants.add(builder);
    }

    private void addPropertyVariant(@NotNull String key, @NotNull JsonSchemaObject jsonSchemaObject, boolean hasValue, boolean insertComma) {
      final String description = jsonSchemaObject.getDescription();
      final String title = jsonSchemaObject.getTitle();
      key = !myWrapInQuotes ? key : StringUtil.wrapWithDoubleQuote(key);
      LookupElementBuilder builder = LookupElementBuilder.create(key);

      String typeText = StringUtil.isEmpty(title) ? description : title;
      if (!StringUtil.isEmpty(typeText)) {
        builder = builder.withTypeText(typeText, true);
      }

      final JsonSchemaType type = jsonSchemaObject.getType();
      final List<Object> values = jsonSchemaObject.getEnum();
      if (type != null || !ContainerUtil.isEmpty(values) || jsonSchemaObject.getDefault() != null) {
        builder = builder.withInsertHandler(createPropertyInsertHandler(jsonSchemaObject, hasValue, insertComma));
      } else if (!hasValue) {
        builder = builder.withInsertHandler(createDefaultPropertyInsertHandler(false, insertComma));
      }

      myVariants.add(builder);
    }

    private InsertHandler<LookupElement> createDefaultPropertyInsertHandler(@SuppressWarnings("SameParameterValue") boolean hasValue,
                                                                            boolean insertComma) {
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          ApplicationManager.getApplication().assertWriteAccessAllowed();
          Editor editor = context.getEditor();
          Project project = context.getProject();

          if (handleInsideQuotesInsertion(context, editor, hasValue)) return;

          // inserting longer string for proper formatting
          final String stringToInsert = ": 1" + (insertComma ? "," : "");
          EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);
          formatInsertedString(context, stringToInsert.length());
          final int offset = editor.getCaretModel().getOffset();
          context.getDocument().deleteString(offset, offset + 1);
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
          AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
        }
      };
    }

    @NotNull
    private InsertHandler<LookupElement> createPropertyInsertHandler(@NotNull JsonSchemaObject jsonSchemaObject,
                                                                     final boolean hasValue,
                                                                     boolean insertComma) {
      JsonSchemaType type = jsonSchemaObject.getType();
      final List<Object> values = jsonSchemaObject.getEnum();
      if (type == null && values != null && !values.isEmpty()) type = detectType(values);
      final Object defaultValue = jsonSchemaObject.getDefault();
      final String defaultValueAsString = defaultValue == null || defaultValue instanceof JsonSchemaObject ? null :
                                          (defaultValue instanceof String ? "\"" + defaultValue + "\"" :
                                                                        String.valueOf(defaultValue));
      JsonSchemaType finalType = type;
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
          ApplicationManager.getApplication().assertWriteAccessAllowed();
          Editor editor = context.getEditor();
          Project project = context.getProject();
          String stringToInsert;
          final String comma = insertComma ? "," : "";

          if (handleInsideQuotesInsertion(context, editor, hasValue)) return;

          if (finalType != null) {
            switch (finalType) {
              case _object:
                stringToInsert = ":{}" + comma;
                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);

                PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                formatInsertedString(context, stringToInsert.length());
                EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
                handler.execute(editor, editor.getCaretModel().getCurrentCaret(),
                                DataManager.getInstance().getDataContext(editor.getContentComponent()));
                break;
              case _boolean:
                String value = String.valueOf(Boolean.TRUE.toString().equals(defaultValueAsString));
                stringToInsert = ":" + value + comma;
                SelectionModel model = editor.getSelectionModel();

                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, stringToInsert.length() - comma.length());
                formatInsertedString(context, stringToInsert.length());
                int start = editor.getSelectionModel().getSelectionStart();
                model.setSelection(start - value.length(), start);
                AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
                break;
              case _array:
                stringToInsert = ":[]" + comma;
                EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 2);

                formatInsertedString(context, stringToInsert.length());
                break;
              case _string:
              case _integer:
                insertPropertyWithEnum(context, editor, defaultValueAsString, values, finalType, comma);
                break;
              default:
            }
          }
          else {
            insertPropertyWithEnum(context, editor, defaultValueAsString, values, null, comma);
          }
        }
      };
    }

    private boolean handleInsideQuotesInsertion(InsertionContext context, Editor editor, boolean hasValue) {
      if (myInsideStringLiteral) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = context.getFile().findElementAt(offset);
        int tailOffset = context.getTailOffset();
        int guessEndOffset = tailOffset + 1;
        if (element != null) {
          int endOffset = element.getTextRange().getEndOffset();
          if (endOffset > tailOffset) {
            context.getDocument().deleteString(tailOffset, endOffset - 1);
          }
        }
        if (hasValue) {
          return true;
        }
        editor.getCaretModel().moveToOffset(guessEndOffset);
      } else editor.getCaretModel().moveToOffset(context.getTailOffset());
      return false;
    }

    @Nullable
    private static JsonSchemaType detectType(List<Object> values) {
      JsonSchemaType type = null;
      for (Object value : values) {
        JsonSchemaType newType = null;
        if (value instanceof Integer) newType = JsonSchemaType._integer;
        if (type != null && !type.equals(newType)) return null;
        type = newType;
      }
      return type;
    }
  }

  public static void insertPropertyWithEnum(InsertionContext context,
                                            Editor editor,
                                            String defaultValue,
                                            List<Object> values,
                                            JsonSchemaType type, String comma) {
    final boolean isNumber = type != null && (JsonSchemaType._integer.equals(type) || JsonSchemaType._number.equals(type)) ||
      type == null && (defaultValue != null &&
                       !StringUtil.isQuotedString(defaultValue) || values != null && ContainerUtil.and(values, v -> !(v instanceof String)));
    boolean hasValues = !ContainerUtil.isEmpty(values);
    boolean hasDefaultValue = !StringUtil.isEmpty(defaultValue);
    String stringToInsert = ":" + (hasDefaultValue ? defaultValue : (isNumber ? "" : "\"\"")) + comma;
    EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, 1);
    if (!isNumber || hasDefaultValue) {
      SelectionModel model = editor.getSelectionModel();
      int caretStart = model.getSelectionStart();
      int newOffset = caretStart + (hasDefaultValue ? defaultValue.length() : 1);
      if (hasDefaultValue && !isNumber) newOffset--;
      model.setSelection(isNumber ? caretStart : (caretStart + 1), newOffset);
      editor.getCaretModel().moveToOffset(newOffset);
    }

    formatInsertedString(context, stringToInsert.length());

    if (hasValues) {
      AutoPopupController.getInstance(context.getProject()).autoPopupMemberLookup(context.getEditor(), null);
    }
  }

  public static void formatInsertedString(@NotNull InsertionContext context, int offset) {
    Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(context.getDocument());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformatText(context.getFile(), context.getStartOffset(), context.getTailOffset() + offset);
  }
}