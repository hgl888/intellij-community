/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.impl.source.tree.java.PsiLambdaExpressionImpl;
import com.intellij.psi.impl.source.tree.java.ReplaceExpressionUtil;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class LambdaExpressionElementType extends JavaStubElementType<FunctionalExpressionStub<PsiLambdaExpression>,PsiLambdaExpression> {
  public LambdaExpressionElementType() {
    super("LAMBDA_EXPRESSION");
  }

  @Override
  public PsiLambdaExpression createPsi(@NotNull ASTNode node) {
    return new PsiLambdaExpressionImpl(node);
  }

  @Override
  public FunctionalExpressionStub<PsiLambdaExpression> createStub(LighterAST tree, LighterASTNode node, StubElement parentStub) {
    return new FunctionalExpressionStub<PsiLambdaExpression>(parentStub, this);
  }

  @Override
  public PsiLambdaExpression createPsi(@NotNull FunctionalExpressionStub<PsiLambdaExpression> stub) {
    return new PsiLambdaExpressionImpl(stub);
  }

  @Override
  public void serialize(@NotNull FunctionalExpressionStub<PsiLambdaExpression> stub, @NotNull StubOutputStream dataStream) throws IOException {
  }

  @NotNull
  @Override
  public FunctionalExpressionStub<PsiLambdaExpression> deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new FunctionalExpressionStub<PsiLambdaExpression>(parentStub, this);
  }

  @Override
  public void indexStub(@NotNull FunctionalExpressionStub<PsiLambdaExpression> stub, @NotNull IndexSink sink) {
  }

  @NotNull
  @Override
  public ASTNode createCompositeNode() {
    return new CompositeElement(this) {
      @Override
      public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
        if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType()) &&
            ElementType.EXPRESSION_BIT_SET.contains(newElement.getElementType())) {
          boolean needParenth = ReplaceExpressionUtil.isNeedParenthesis(child, newElement);
          if (needParenth) {
            newElement = JavaSourceUtil.addParenthToReplacedChild(JavaElementType.PARENTH_EXPRESSION, newElement, getManager());
          }
        }
        super.replaceChildInternal(child, newElement);
      }

      @Override
      public int getChildRole(ASTNode child) {
        final IElementType elType = child.getElementType();
        if (elType == JavaTokenType.ARROW) {
          return ChildRole.ARROW;
        } else if (elType == JavaElementType.PARAMETER_LIST) {
          return ChildRole.PARAMETER_LIST;
        } else if (elType == JavaElementType.CODE_BLOCK) {
          return ChildRole.LBRACE;
        } else {
          return ChildRole.EXPRESSION;
        }
      }
    };
  }
}
