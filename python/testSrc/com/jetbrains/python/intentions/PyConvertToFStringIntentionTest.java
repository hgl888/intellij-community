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
package com.jetbrains.python.intentions;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;

/**
 * @author Mikhail Golubev
 */
public class PyConvertToFStringIntentionTest extends PyIntentionTestCase {
  
  private void doTest() {
    doTest(PyBundle.message("INTN.convert.to.fstring.literal"), LanguageLevel.PYTHON36);
  }

  private void doNegativeTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON36, () -> doNegativeTest(PyBundle.message("INTN.convert.to.fstring.literal")));
  }
  
  public void testFormatMethodByteString() {
    doNegativeTest();
  }

  public void testFormatMethodGluedString() {
    doNegativeTest();
  }

  public void testPercentOperatorDynamicWidth() {
    doNegativeTest();
  }

  public void testPercentOperatorDynamicPrecision() {
    doNegativeTest();
  }

  public void testPercentOperatorSingleExpression() {
    doTest();
  }

  public void testPercentOperatorSimpleTuple() {
    doTest();
  }

  public void testPercentOperatorSimpleDictLiteral() {
    doTest();
  }

  public void testPercentOperatorSimpleDictConstructorCall() {
    doTest();
  }

  public void testPercentOperatorQuotesInsideInlinedExpressions() {
    doTest();
  }

  public void testPercentOperatorMultilineExpression() {
    doNegativeTest();
  }

  public void testPercentOperatorExpressionWithBackslash() {
    doNegativeTest();    
  }

  public void testPercentOperatorExpressionContainsOriginalHostQuote() {
    doNegativeTest();
  }
  
  public void testPercentOperatorExpressionContainsAlternativeHostQuote() {
    doNegativeTest();
  }

  public void testFormatMethodSimple() {
    doTest();
  }

  public void testFormatMethodAttributeReferences() {
    doTest();
  }

  public void testFormatMethodItemAccess() {
    doTest();
  }

  public void testFormatMethodIndexContainsHostAlternativeQuote() {
    doTest();
  }
  
  public void testFormatMethodIndexContainsBackslash() {
    doTest();
  }

  public void testPercentOperatorWidthAndPrecision() {
    doTest();
  }

  public void testFormatMethodWrongStringMethod() {
    doNegativeTest();
  }

  public void testPercentOperatorInlineMultilineTripleQuotedString() {
    doNegativeTest();
  }

  public void testPercentOperatorInlineOneLineTripleQuotedString() {
    doTest();
  }

  public void testPercentOperatorInlineSingleQuotedStringInsideTripleQuotedString() {
    doTest();
  }

  // PY-21244
  public void testFormatMethodNestedFields() {
    doTest();
  }
  
  // PY-21244
  public void testFormatMethodNestedFields2() {
    doTest();
  }
}
