/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.json.JsonLanguage;
import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.ide.JsonSchemaDocumentationProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author Irina.Chernushina on 2/20/2017.
 */
public abstract class JsonBySchemaDocumentationBaseTest extends JsonSchemaHeavyAbstractTest {
  protected void doTest(boolean hasDoc, String extension) throws Exception {
    final JsonSchemaDocumentationProvider provider = new JsonSchemaDocumentationProvider();
    LanguageDocumentation.INSTANCE.addExplicitExtension(JsonLanguage.INSTANCE, provider);

    try {
      skeleton(new Callback() {
        @Override
        public void registerSchemes() {
          final String moduleDir = getModuleDir(getProject());
          final ArrayList<JsonSchemaMappingsConfigurationBase.Item> patterns = new ArrayList<>();
          patterns.add(new JsonSchemaMappingsConfigurationBase.Item(getTestName(true) + "*", true, false));
          addSchema(
            new JsonSchemaMappingsConfigurationBase.SchemaInfo("testDoc", moduleDir + "/" + getTestName(true) + "Schema.json", false,
                                                               patterns));
        }

        @Override
        public void configureFiles() throws Exception {
          configureByFiles(null, "/" + getTestName(true) + "." + extension, "/" + getTestName(true) + "Schema.json");
        }

        @Override
        public void doCheck() {
          PsiElement psiElement = DocumentationManager.getInstance(getProject()).findTargetElement(myEditor, myFile);
          assertDocumentation(psiElement, psiElement, hasDoc);
        }
      });
    } finally {
      LanguageDocumentation.INSTANCE.removeExplicitExtension(JsonLanguage.INSTANCE, provider);
      JsonSchemaTestServiceImpl.setProvider(null);
    }
  }

  protected void assertDocumentation(@NotNull PsiElement docElement, @NotNull PsiElement context, boolean shouldHaveDoc) {
    DocumentationProvider documentationProvider = DocumentationManager.getProviderFromElement(context);
    String inlineDoc = documentationProvider.generateDoc(docElement, context);
    if (shouldHaveDoc) {
      assertNotNull("inline help is null", inlineDoc);
    }
    else {
      assertNull("inline help is not null", inlineDoc);
    }
    if (shouldHaveDoc) {
      assertSameLinesWithFile(getTestDataPath() + "/" + getTestName(true) + ".html", inlineDoc);
    }
  }
}
