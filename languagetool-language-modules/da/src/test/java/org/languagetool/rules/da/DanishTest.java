/* LanguageTool, a natural language style checker
 * Copyright (C) 2019 Daniel Naber (http://www.danielnaber.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package org.languagetool.rules.da;

import org.junit.Test;
import org.languagetool.LanguageSpecificTest;
import org.languagetool.language.Danish;
import org.languagetool.rules.WordListValidatorTest;

import java.io.IOException;
import java.util.Arrays;

public class DanishTest extends LanguageSpecificTest {
  
  @Test
  public void testLanguage() throws IOException {
    // NOTE: this text needs to be kept in sync with WelcomeController.php's getDefaultDemoTexts():
    String s = "Indsæt din egen tekst her , eller brug denne tekst for at se nogle af de fejl LanguageTool fanger. vær opmærksom på at den langtfra er er perfect, men skal være en hjælp til at få standartfejl frem i lyset.";
    Danish lang = new Danish();
    testDemoText(lang, s,
      Arrays.asList("COMMA_PARENTHESIS_WHITESPACE", "UPPERCASE_SENTENCE_START", "Ordgentagelse", "HUNSPELL_NO_SUGGEST_RULE", "standart_standard")
    );
    runTests(lang);
  }
}
