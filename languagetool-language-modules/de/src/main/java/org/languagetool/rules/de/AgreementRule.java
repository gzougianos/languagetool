/* LanguageTool, a natural language style checker
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
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
package org.languagetool.rules.de;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.languagetool.*;
import org.languagetool.language.German;
import org.languagetool.rules.Categories;
import org.languagetool.rules.Example;
import org.languagetool.rules.Rule;
import org.languagetool.rules.RuleMatch;
import org.languagetool.rules.patterns.PatternToken;
import org.languagetool.rules.patterns.PatternTokenBuilder;
import org.languagetool.tagging.de.AnalyzedGermanToken;
import org.languagetool.tagging.de.GermanToken;
import org.languagetool.tagging.de.GermanToken.POSType;
import org.languagetool.tagging.disambiguation.rules.DisambiguationPatternRule;
import org.languagetool.tools.StringTools;
import org.languagetool.tools.Tools;

/**
 * Simple agreement checker for German noun phrases. Checks agreement in:
 *
 * <ul>
 *  <li>DET/PRO NOUN: e.g. "mein Auto", "der Mann", "die Frau" (correct), "die Haus" (incorrect)</li>
 *  <li>DET/PRO ADJ NOUN: e.g. "der riesige Tisch" (correct), "die riesigen Tisch" (incorrect)</li>
 * </ul>
 *
 * Note that this rule only checks agreement inside the noun phrase, not whether
 * e.g. the correct case is used. For example, "Es ist das Haus dem Mann" is not
 * detected as incorrect.
 *
 * <p>TODO: the implementation could use a re-write that first detects the relevant noun phrases and then checks agreement
 *
 * @author Daniel Naber
 */
public class AgreementRule extends Rule {

  private final German language;

  private JLanguageTool lt;

  private enum GrammarCategory {
    KASUS("Kasus (Fall: Wer/Was, Wessen, Wem, Wen/Was - Beispiel: 'das Fahrrads' statt 'des Fahrrads')"),
    GENUS("Genus (männlich, weiblich, sächlich - Beispiel: 'der Fahrrad' statt 'das Fahrrad')"),
    NUMERUS("Numerus (Einzahl, Mehrzahl - Beispiel: 'das Fahrräder' statt 'die Fahrräder')");

    private final String displayName;
    GrammarCategory(String displayName) {
      this.displayName = displayName;
    }
  }
  private static final AnalyzedToken[] INS_REPLACEMENT = {new AnalyzedToken("das", "ART:DEF:AKK:SIN:NEU", "das")};
  private static final AnalyzedToken[] ZUR_REPLACEMENT = {new AnalyzedToken("der", "ART:DEF:DAT:SIN:FEM", "der")};

  private static final List<List<PatternToken>> ANTI_PATTERNS = Arrays.asList(
    Arrays.asList(  // "wird das schwere Konsequenzen haben"
      token("das"),
      token("schwere"),
      token("Konsequenzen")
    ),
    Arrays.asList(  // "der Chaos Computer Club"
      token("der"),
      token("Chaos"),
      token("Computer"),
      token("Club")
    ),
    Arrays.asList(  // "In einem App Store"
      token("App"),
      token("Store")
    ),
    Arrays.asList(  // "in dem einen Jahr"
      token("dem"),
      token("einen"),
      pos("SUB:NOM:SIN:NEU")
    ),
    Arrays.asList(  // "Dies erlaubt Forschern, ..."
      posRegex("PRO:DEM:.+"),
      posRegex("PA2:.+"),
      posRegex("SUB:.*:PLU.*")
    ),
    Arrays.asList(  // "Wir bereinigen das nächsten Dienstag."
      posRegex("VER:.*|UNKNOWN"),
      token("das"),
      tokenRegex("(über)?nächste[ns]?|kommende[ns]?|(vor)?letzten"),
      tokenRegex("Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember|Montag|D(ien|onner)stag|Mittwoch|Freitag|S(ams|onn)tag|Sonnabend|Woche|Monat|Jahr|Morgens?|Abends|Übermorgen|Mittags?|Nachmittags?|Vormittags?|Spätabends?|Nachts?")
    ),
    Arrays.asList(  // "Wir releasen das Montag.", "Wir präsentierten das Januar."
      posRegex("VER:.*|UNKNOWN"),
      token("das"),
      tokenRegex("Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember|Montags?|D(ien|onner)stags?|Mittwochs?|Freitags?|S(ams|onn)tags?|Sonnabends?|Morgens?|Abends|Übermorgen|Mittags?|Nachmittags?|Vormittags?|Spätabends?|Nachts?")
    ),
    Arrays.asList(  // "Kannst du das Mittags machen?"
      token("das"),
      tokenRegex("Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember|Montags?|D(ien|onner)stags?|Mittwochs?|Freitags?|S(ams|onn)tags?|Sonnabends?|Morgens?|Abends|Übermorgen|Mittags?|Nachmittags?|Vormittags?|Spätabends?|Nachts?"),
      posRegex("VER:.*|UNKNOWN")
    ),
    Arrays.asList(  // "Kannst du das nächsten Monat machen?"
      token("das"),
      tokenRegex("(über)?nächste[ns]?|kommende[ns]?|(vor)?letzten"),
      tokenRegex("Januar|Februar|März|April|Mai|Juni|Juli|August|September|Oktober|November|Dezember|Montag|D(ien|onner)stag|Mittwoch|Freitag|S(ams|onn)tag|Sonnabend|Woche|Monat|Jahr|Morgens?|Abends|Übermorgen|Mittags?|Nachmittags?|Vormittags?|Spätabends?|Nachts?"),
      posRegex("VER:.*|UNKNOWN")
    ),
    Arrays.asList(
      token("das"),
      tokenRegex("Zufall|Sinn|Spaß"),
      token("?")
    ),
    Arrays.asList(
      token("in"),
      tokenRegex("d(ies)?em"),
      token("Fall"),
      tokenRegex("(?i:hat(te)?)"),
      token("das")
    ),
    Arrays.asList( // "So hatte das Vorteile|Auswirkungen|Konsequenzen..."
      posRegex("ADV:.+"),
      tokenRegex("(?i:hat(te)?)"),
      token("das")
    ),
    Arrays.asList(
      tokenRegex("von|bei"),
      tokenRegex("(vielen|allen)"),
      posRegex("PA2:.*|ADJ:AKK:PLU:.*")  // "ein von vielen bewundertes Haus" / "Das weckte bei vielen ungute Erinnerungen."
    ),
    Arrays.asList(
      token("für"),
      tokenRegex("(viele|alle|[dm]ich|ihn|sie|uns)"),
      posRegex("ADJ:AKK:.*")  // "Ein für viele wichtiges Anliegen."
    ),
    Arrays.asList(
      new PatternTokenBuilder().tokenRegex("flößen|machen|jagen").matchInflectedForms().build(),
      token("einem"),
      token("Angst")  // "Dinge, die/ Etwas, das einem Angst macht"
    ),
    Arrays.asList(
      token("einem"),
      token("Angst"),  // "Was einem Angst macht"
      new PatternTokenBuilder().tokenRegex("machen|ein(flößen|jagen)").matchInflectedForms().build()
    ),
    Arrays.asList(
      token("einem"),
      token("geschenkten"),
      token("Gaul")
    ),
    Arrays.asList(
      token("kein"),
      token("schöner"),
      token("Land")  // https://de.wikipedia.org/wiki/Kein_sch%C3%B6ner_Land
    ),
    Arrays.asList(
      tokenRegex("die|der|das"),
      tokenRegex("Anfang|Ende"),
      tokenRegex("Januar|Jänner|Februar|März|April|Mai|Ju[ln]i|August|September|Oktober|November|Dezember|[12][0-9]{3}")
    ),
    Arrays.asList(
      pos(JLanguageTool.SENTENCE_START_TAGNAME),
      tokenRegex("Ist|Sind|Macht|Wird"),
      token("das"),
      posRegex("SUB:.*"),
      posRegex("PKT|KON:NEB|ZUS")// "Ist das Kunst?" / "Ist das Kunst oder Abfall?" / "Sind das Eier aus Bodenhaltung"
    ),
    Arrays.asList(
      // like above, but with ":", as we don't interpret this as a sentence start (but it often is)
      token(":"),
      tokenRegex("Ist|Sind|Macht|Wird"),
      token("das"),
      posRegex("SUB:.*"),
      posRegex("PKT|KON:NEB|ZUS")// "Ist das Kunst?" / "Ist das Kunst oder Abfall?" / "Sind das Eier aus Bodenhaltung"
    ),
    Arrays.asList(
      pos(JLanguageTool.SENTENCE_START_TAGNAME),
      tokenRegex("Meist(ens)?|Oft(mals)?|Häufig|Selten"),
      tokenRegex("sind|waren|ist"),
      token("das"),
      posRegex("SUB:.*") // Meistens sind das Frauen, die damit besser umgehen können.
    ),
    Arrays.asList(
      // like above, but with ":", as we don't interpret this as a sentence start (but it often is)
      token(":"),
      tokenRegex("Meist(ens)?|Oft(mals)?|Häufig|Selten"),
      tokenRegex("sind|waren|ist"),
      token("das"),
      posRegex("SUB:.*") // Meistens sind das Frauen, die damit besser umgehen können.
    ),
    Arrays.asList(
      token("des"),
      token("Lied"),
      token("ich") // Wes Brot ich ess, des Lied ich sing
    ),
    Arrays.asList(
      pos(JLanguageTool.SENTENCE_START_TAGNAME),
      tokenRegex("D(a|ie)s"),
      posRegex("VER:[123]:.*"),
      posRegex("SUB:NOM:.*")// "Das erfordert Können und..." / "Dies bestätigte Polizeimeister Huber"
    ),
    Arrays.asList(
      // like above, but with ":", as we don't interpret this as a sentence start (but it often is)
      token(":"),
      tokenRegex("D(a|ie)s"),
      posRegex("VER:[123]:.*"),
      posRegex("SUB:NOM:.*")// "Das erfordert Können und..." / "Dies bestätigte Polizeimeister Huber"
    ),
    Arrays.asList(
      posRegex("ART:.+"), // "Das wenige Kilometer breite Tal"
      posRegex("ADJ:.+"),
      tokenRegex("(Kilo|Zenti|Milli)?meter|Jahre|Monate|Wochen|Tage|Stunden|Minuten|Sekunden")
    ),
    Arrays.asList(
      token("Van"), // https://de.wikipedia.org/wiki/Alexander_Van_der_Bellen
      token("der"),
      tokenRegex("Bellens?")
    ),
    Arrays.asList(
      token("mehrere"), // "mehrere Verwundete" http://forum.languagetool.org/t/de-false-positives-and-false-false/1516
      pos("SUB:NOM:SIN:FEM:ADJ")
    ),
    Arrays.asList(
      token("allen"),
      tokenRegex("Besitz|Mut")
    ),
    Arrays.asList(
      tokenRegex("d(ie|e[nr])|[md]eine[nr]?"),
      token("Top"),
      tokenRegex("\\d+")
    ),
    Arrays.asList( //"Unter diesen rief das großen Unmut hervor."
      posRegex("VER:3:SIN:.*"),
      token("das"),
      posRegex("ADJ:AKK:.*"),
      posRegex("SUB:AKK:.*"),
      pos("ZUS"),
      pos(JLanguageTool.SENTENCE_END_TAGNAME)
    ),
    Arrays.asList( // "Bei mir löste das Panik aus."
      posRegex("VER:3:SIN:.+"),
      token("das"),
      posRegex("SUB:AKK:.+"),
      pos("ZUS"),
      pos(JLanguageTool.SENTENCE_END_TAGNAME)
    ),
    Arrays.asList(
      token("Außenring"),
      token("Autobahn")
    ),
    Arrays.asList( // "Ehre, wem Ehre gebührt"
      tokenRegex("[dw]em"),
      csToken("Ehre"),
      csToken("gebührt")
    ),
    Arrays.asList(
      token("Eurovision"),
      token("Song"),
      token("Contest")
    ),
    Arrays.asList(
      token("Junge"),
      tokenRegex("Union|Freiheit|Welt|Europäische|Alternative|Volkspartei|Akademie")
    ),
    Arrays.asList( // "Das Holocaust Memorial Museum."
      posRegex("ART:.+"),
      posRegex("SUB:.+"),
      pos("UNKNOWN")
    ),
    Arrays.asList( // "Er fragte, ob das Spaß macht."
      csToken(","),
      posRegex("KON:UNT|ADV:INR"),
      csToken("das"),
      posRegex("SUB:.+"),
      posRegex("VER:3:SIN.*")
    ),
    Arrays.asList( // "Es gibt viele solcher Bilder"
      tokenRegex("viele|wenige|einige|mehrere"),
      csToken("solcher"),
      posRegex("SUB:GEN:PLU:.*")
    ),
    Arrays.asList( // "der französischen First Lady"
      tokenRegex("[dD](ie|er)"),
      csToken("First"),
      csToken("Lady")
    ),
    Arrays.asList( // "der französischen First Lady"
      tokenRegex("[dD](ie|er)"),
      posRegex("ADJ:.*"),
      csToken("First"),
      csToken("Lady")
    ),
    Arrays.asList( // "der Super Nintendo"
      tokenRegex("[dD](ie|er)"),
      csToken("Super"),
      csToken("Nintendo")
    ),
    Arrays.asList( // Texas und New Mexico, beides spanische Kolonien, sind
      csToken(","),
      csToken("beides"),
      posRegex("ADJ:NOM:PLU.+"),
      posRegex("SUB:NOM:PLU.+"),
      csToken(",")
    ),
    Arrays.asList(
      tokenRegex("[dD]e[rn]"),
      csToken("Gold"),
      csToken("Cup")
    ),
    Arrays.asList(
      token("das"),
      tokenRegex("viele|wenige"),
      posRegex("SUB:.*")
    ),
    Arrays.asList( // "Er verspricht allen/niemandem/jedem hohe Gewinne."
      tokenRegex("allen|(nieman|je(man)?)dem"),
      posRegex("ADJ:AKK:PLU:.*"),
      posRegex("SUB:AKK:PLU:.*")
    ),
    Arrays.asList( // "Er verspricht allen/niemandem/jedem Gewinne von über 15 Prozent."
      tokenRegex("allen|(nieman|je(man)?)dem"),
      posRegex("SUB:AKK:PLU:.*")
    ),
    Arrays.asList( // "Für ihn ist das Alltag." / "Für die Religiösen ist das Blasphemie und führt zu Aufständen."
      new PatternTokenBuilder().posRegex("PRP:.+|ADV:MOD").setSkip(2).build(),
      new PatternTokenBuilder().token("sein").matchInflectedForms().build(),
      csToken("das"),
      posRegex("SUB:NOM:.*"),
      posRegex("PKT|SENT_END|KON.*")
    ),
    Arrays.asList( // "Sie sagte, dass das Rache bedeuten würden"
      pos("KON:UNT"),
      csToken("das"),
      posRegex("SUB:.+"),
      new PatternTokenBuilder().tokenRegex("bedeuten|sein").matchInflectedForms().build()
    ),
    Arrays.asList( // "Sie fragte, ob das wirklich Rache bedeuten würde"
      pos("KON:UNT"),
      csToken("das"),
      pos("ADV:MOD"),
      posRegex("SUB:.+"),
      new PatternTokenBuilder().tokenRegex("bedeuten|sein").matchInflectedForms().build()
    ),
    Arrays.asList( // "Karl sagte, dass sie niemandem Bescheid gegeben habe."
      new PatternTokenBuilder().token("niemand").matchInflectedForms().build(),
      posRegex("SUB:.+")
    ),
    Arrays.asList(
      token("alles"),
      csToken("Walzer")
    ),
    Arrays.asList( // "ei der Daus"
      csToken("der"),
      csToken("Daus")
    ),
    Arrays.asList( // "Das Orange ist meine Lieblingsfarbe"
      posRegex("PRO:...:...:SIN:NEU.*"),
      csToken("Orange")
    ),
    Arrays.asList( // "Dieses rötliche Orange gefällt mir am besten"
      posRegex("PRO:...:...:SIN:NEU.*"),
      posRegex("ADJ:.+"),
      csToken("Orange")
    ),
    Arrays.asList(
      csToken("dem"),
      new PatternTokenBuilder().csToken("Achtung").setSkip(1).build(),
      new PatternTokenBuilder().csToken("schenken").matchInflectedForms().build()
    ),
    Arrays.asList(
      new PatternTokenBuilder().csToken("schenken").matchInflectedForms().build(),
      csToken("dem"),
      csToken("Achtung")
    ),
    Arrays.asList(
      csToken("dem"),
      new PatternTokenBuilder().csToken("Rechnung").setSkip(1).build(),
      new PatternTokenBuilder().csToken("tragen").matchInflectedForms().build()
    ),
    Arrays.asList(
      new PatternTokenBuilder().csToken("tragen").matchInflectedForms().build(),
      csToken("dem"),
      csToken("Rechnung")
    ),
    Arrays.asList(
      csToken("zum"),
      csToken("einen"),
      posRegex("ADJ:.+")
    ),
    Arrays.asList(
      token("auf"),
      csToken("die"),
      csToken("Lauer")
    ),
    Arrays.asList(
      token("dieser"),
      csToken("eine"),
      pos("SUB:NOM:SIN:MAS")
    ),
    Arrays.asList(
      token("das"),
      posRegex("SUB:DAT:.+"),
      token("vorbehalten")
    ),
    Arrays.asList( // Wenn hier einer Geld hat, dann ich.
      new PatternTokenBuilder().token("wenn").setSkip(1).build(),
      csToken("einer"),
      posRegex("SUB:AKK:.+"),
      posRegex("VER:(MOD:)?3:SIN:.+"),
      csToken(",")
    ),
    Arrays.asList( // Es ist nicht eines jeden Bestimmung
      tokenRegex("eine[rs]"),
      tokenRegex("jed(wed)?en")
    ),
    Arrays.asList( // Ich vertraue auf die Meinen.
      token("die"),
      tokenRegex("[MDS]einen")
    ),
    Arrays.asList( // Sie ist über die Maßen schön.
      csToken("über"),
      csToken("die"),
      csToken("Maßen")
    ),
    Arrays.asList( // Was nützt einem Gesundheit, wenn man sonst ein Idiot ist?
      token("was"),
      new PatternTokenBuilder().csToken("nützen").matchInflectedForms().build(),
      csToken("einem"),
      posRegex("SUB:NOM:.+")
    ),
    Arrays.asList( // Auch das hat sein Gutes.
      new PatternTokenBuilder().csToken("haben").matchInflectedForms().build(),
      csToken("sein"),
      csToken("Gutes")
    ),
    Arrays.asList( // Auch wenn es sein Gutes hatte.
      csToken("Gutes"),
      new PatternTokenBuilder().tokenRegex("haben|tun").matchInflectedForms().build()
    ),
    Arrays.asList(
      csToken("dieser"),
      csToken("einen"),
      pos("SUB:DAT:SIN:FEM")
    ),
    Arrays.asList(
      posRegex("ABK:.+:SUB")
    ),
    Arrays.asList(
      tokenRegex("(all|je(d|glich))en"),
      csToken("Reiz")
    ),
    Arrays.asList(
      new PatternTokenBuilder().posRegex("VER:.*[1-3]:.+").setSkip(1).build(),
      csToken("vermehrt")
    )
  );

  private static PatternToken tokenRegex(String s) {
    return new PatternTokenBuilder().tokenRegex(s).build();
  }

  private static PatternToken posRegex(String s) {
    return new PatternTokenBuilder().posRegex(s).build();
  }

  private static PatternToken csToken(String s) {
    return new PatternTokenBuilder().csToken(s).build();
  }

  private static PatternToken pos(String s) {
    return new PatternTokenBuilder().pos(s).build();
  }

  private static PatternToken token(String s) {
    return new PatternTokenBuilder().token(s).build();
  }

  private static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList(
      "besonders",
      "fast",
      "ganz",
      "geradezu",
      "sehr",
      "überaus",
      "ziemlich"
    ));

  private static final Set<String> VIELE_WENIGE_LOWERCASE = new HashSet<>(Arrays.asList(
    "sämtlicher",
    "etliche",
    "etlicher",
    "viele",
    "vieler",
    "wenige",
    "weniger",
    "einige",
    "einiger",
    "mehrerer",
    "mehrere"
  ));

  private static final String[] REL_PRONOUN_LEMMAS = {"der", "welch"};

  private static final Set<String> PRONOUNS_TO_BE_IGNORED = new HashSet<>(Arrays.asList(
    "ich",
    "dir",
    "du",
    "er", "sie", "es",
    "wir",
    "mir",
    "uns",
    "ihnen",
    "euch",
    "ihm",
    "ihr",
    "ihn",
    "dessen",
    "deren",
    "denen",
    "sich",
    "aller",
    "man",
    "beide",
    "beiden",
    "beider",
    "wessen",
    "a",
    "alle",
    "etwas",
    "irgendetwas",
    "was",
    "wer",
    "jenen",      // "...und mit jenen anderer Arbeitsgruppen verwoben"
    "diejenigen",
    "jemand", "jemandes",
    "niemand", "niemandes"
  ));

  private static final Set<String> NOUNS_TO_BE_IGNORED = new HashSet<>(Arrays.asList(
    "Prozent",   // Plural "Prozente", trotzdem ist "mehrere Prozent" korrekt
    "Gramm",
    "Kilogramm",
    "Meter", // Das Meter (Objekt zum Messen)
    "Boots", // "Die neuen Boots" (englisch Stiefel)
    "Taxameter", // Beides erlaubt "Das" und "Die"
    "Bild", // die Bild (Zeitung)
    "Uhr"   // "um ein Uhr"
  ));

  public AgreementRule(ResourceBundle messages, German language) {
    this.language = language;
    super.setCategory(Categories.GRAMMAR.getCategory(messages));
    addExamplePair(Example.wrong("<marker>Der Haus</marker> wurde letztes Jahr gebaut."),
                   Example.fixed("<marker>Das Haus</marker> wurde letztes Jahr gebaut."));
  }

  @Override
  public String getId() {
    return "DE_AGREEMENT";
  }

  @Override
  public int estimateContextForSureMatch() {
    return ANTI_PATTERNS.stream().mapToInt(List::size).max().orElse(0);
  }

  @Override
  public String getDescription() {
    return "Kongruenz von Nominalphrasen (unvollständig!), z.B. 'mein kleiner(kleines) Haus'";
  }

  private void replacePrepositionsByArticle (AnalyzedTokenReadings[] tokens) {
    for (int i = 0; i < tokens.length; i++) {
      if (StringUtils.equalsAny(tokens[i].getToken(), "ins", "ans", "aufs", "vors", "durchs", "hinters", "unters", "übers", "fürs", "ums")) {
        tokens[i] = new AnalyzedTokenReadings(INS_REPLACEMENT, tokens[i].getStartPos());
      } else if (StringUtils.equalsAny(tokens[i].getToken(), "zur")) {
        tokens[i] = new AnalyzedTokenReadings(ZUR_REPLACEMENT, tokens[i].getStartPos());
      }
    }
  }

  @Override
  public RuleMatch[] match(AnalyzedSentence sentence) {
    List<RuleMatch> ruleMatches = new ArrayList<>();
    AnalyzedTokenReadings[] tokens = getSentenceWithImmunization(sentence).getTokensWithoutWhitespace();
    replacePrepositionsByArticle(tokens);
    for (int i = 0; i < tokens.length; i++) {
      //defaulting to the first reading
      //TODO: check for all readings
      String posToken = tokens[i].getAnalyzedToken(0).getPOSTag();
      if (JLanguageTool.SENTENCE_START_TAGNAME.equals(posToken) || tokens[i].isImmunized()) {
        continue;
      }

      AnalyzedTokenReadings tokenReadings = tokens[i];
      boolean relevantPronoun = isRelevantPronoun(tokens, i);

      boolean ignore = couldBeRelativeOrDependentClause(tokens, i);
      if (i > 0) {
        String prevToken = tokens[i-1].getToken().toLowerCase();
        if (StringUtils.equalsAny(tokens[i].getToken(), "eine", "einen")
            && StringUtils.equalsAny(prevToken, "der", "die", "das", "des", "dieses")) {
          // TODO: "der eine Polizist" -> nicht ignorieren, sondern "der polizist" checken; "auf der einen Seite"
          ignore = true;
        }
      }

      // avoid false alarm on "nichts Gutes" and "alles Gute"
      if (StringUtils.equalsAny(tokenReadings.getToken(), "nichts", "alles", "dies")) {
        ignore = true;
      }

      // avoid false alarm on "Art. 1" and "bisherigen Art. 1" (Art. = Artikel):
      boolean detAbbrev = i < tokens.length-2 && tokens[i+1].getToken().equals("Art") && tokens[i+2].getToken().equals(".");
      boolean detAdjAbbrev = i < tokens.length-3 && tokens[i+2].getToken().equals("Art") && tokens[i+3].getToken().equals(".");
      // "einen Hochwasser führenden Fluss", "die Gott zugeschriebenen Eigenschaften":
      boolean followingParticiple = i < tokens.length-3 && (tokens[i+2].hasPartialPosTag("PA1") || tokens[i+2].getToken().matches("zugeschriebenen?|genannten?"));
      if (detAbbrev || detAdjAbbrev || followingParticiple) {
        ignore = true;
      }

      if ((GermanHelper.hasReadingOfType(tokenReadings, POSType.DETERMINER) || relevantPronoun) && !ignore) {
        int tokenPosAfterModifier = getPosAfterModifier(i+1, tokens);
        int tokenPos = tokenPosAfterModifier;
        if (tokenPos >= tokens.length) {
          break;
        }
        AnalyzedTokenReadings nextToken = tokens[tokenPos];
        if (isNonPredicativeAdjective(nextToken) || isParticiple(nextToken)) {
          tokenPos = tokenPosAfterModifier + 1;
          if (tokenPos >= tokens.length) {
            break;
          }
          if (GermanHelper.hasReadingOfType(tokens[tokenPos], POSType.NOMEN)) {
            // TODO: add a case (checkAdjNounAgreement) for special cases like "deren",
            // e.g. "deren komisches Geschenke" isn't yet detected as incorrect
            if (i >= 2 && GermanHelper.hasReadingOfType(tokens[i-2], POSType.ADJEKTIV)
                       && "als".equals(tokens[i-1].getToken())
                       && "das".equals(tokens[i].getToken())) {
              // avoid false alarm for e.g. "weniger farbenprächtig als das anderer Papageien"
              continue;
            }
            RuleMatch ruleMatch = checkDetAdjNounAgreement(tokens[i],
                nextToken, tokens[tokenPos], sentence, i);
            if (ruleMatch != null) {
              ruleMatches.add(ruleMatch);
            }
          }
        } else if (GermanHelper.hasReadingOfType(nextToken, POSType.NOMEN) && !"Herr".equals(nextToken.getToken())) {
          RuleMatch ruleMatch = checkDetNounAgreement(tokens[i], nextToken, sentence, i);
          if (ruleMatch != null) {
            ruleMatches.add(ruleMatch);
          }
        }
      }
    } // for each token
    return toRuleMatchArray(ruleMatches);
  }

  /**
   * Search for modifiers (such as "sehr", "1,4 Meter") which can expand a
   * determiner - adjective - noun group ("ein hohes Haus" -> "ein sehr hohes Haus",
   * "ein 500 Meter hohes Haus") and return the index of the first non-modifier token ("Haus")
   * @param startAt index of array where to start searching for modifier
   * @return index of first non-modifier token
   */
  private int getPosAfterModifier(int startAt, AnalyzedTokenReadings[] tokens) {
    if ((startAt + 1) < tokens.length && MODIFIERS.contains(tokens[startAt].getToken())) {
      startAt++;
    }
    if ((startAt + 1) < tokens.length && (StringUtils.isNumeric(tokens[startAt].getToken()) || tokens[startAt].hasPosTag("ZAL"))) {
      int posAfterModifier = startAt + 1;
      if ((startAt + 3) < tokens.length && ",".equals(tokens[startAt+1].getToken()) && StringUtils.isNumeric(tokens[startAt+2].getToken())) {
        posAfterModifier = startAt + 3;
      }
      if (StringUtils.endsWithAny(tokens[posAfterModifier].getToken(), "gramm", "Gramm", "Meter", "meter")) {
        return posAfterModifier + 1;
      }
    }
    return startAt;
  }

  @Override
  public List<DisambiguationPatternRule> getAntiPatterns() {
    return makeAntiPatterns(ANTI_PATTERNS, language);
  }

  private boolean isNonPredicativeAdjective(AnalyzedTokenReadings tokensReadings) {
    for (AnalyzedToken reading : tokensReadings.getReadings()) {
      String posTag = reading.getPOSTag();
      if (posTag != null && posTag.startsWith("ADJ") && !posTag.contains("PRD")) {
        return true;
      }
    }
    return false;
  }

  private boolean isParticiple(AnalyzedTokenReadings tokensReadings) {
    return tokensReadings.hasPartialPosTag("PA1") || tokensReadings.hasPartialPosTag("PA2");
  }

  private boolean isRelevantPronoun(AnalyzedTokenReadings[] tokens, int pos) {
    AnalyzedTokenReadings analyzedToken = tokens[pos];
    boolean relevantPronoun = GermanHelper.hasReadingOfType(analyzedToken, POSType.PRONOMEN);
    // avoid false alarms:
    String token = tokens[pos].getToken();
    if (PRONOUNS_TO_BE_IGNORED.contains(token.toLowerCase()) ||
        (pos > 0 && tokens[pos-1].getToken().equalsIgnoreCase("vor") && token.equalsIgnoreCase("allem"))) {
      relevantPronoun = false;
    }
    return relevantPronoun;
  }

  // TODO: improve this so it only returns true for real relative clauses
  private boolean couldBeRelativeOrDependentClause(AnalyzedTokenReadings[] tokens, int pos) {
    boolean comma;
    boolean relPronoun;
    if (pos >= 1) {
      // avoid false alarm: "Das Wahlrecht, das Frauen zugesprochen bekamen." etc:
      comma = tokens[pos-1].getToken().equals(",");
      relPronoun = comma && tokens[pos].hasAnyLemma(REL_PRONOUN_LEMMAS);
      if (relPronoun && pos+3 < tokens.length) {
        return true;
      }
    }
    if (pos >= 2) {
      // avoid false alarm: "Der Mann, in dem quadratische Fische schwammen."
      // or: "Die Polizei erwischte die Diebin, weil diese Ausweis und Visitenkarte hinterließ."
      comma = tokens[pos-2].getToken().equals(",");
      if(comma) {
        boolean prep = tokens[pos-1].hasPosTagStartingWith("PRP:");
        relPronoun = tokens[pos].hasAnyLemma(REL_PRONOUN_LEMMAS);
        return prep && relPronoun || (tokens[pos-1].hasPosTag("KON:UNT") && (tokens[pos].hasLemma("jen") || tokens[pos].hasLemma("dies")));
      }
    }
    return false;
  }

  @Nullable
  private RuleMatch checkDetNounAgreement(AnalyzedTokenReadings token1,
      AnalyzedTokenReadings token2, AnalyzedSentence sentence, int tokenPos) {
    // TODO: remove "-".equals(token2.getToken()) after the bug fix
    // see Daniel's comment from 20.12.2016 at https://github.com/languagetool-org/languagetool/issues/635
    if (token2.isImmunized() || NOUNS_TO_BE_IGNORED.contains(token2.getToken()) || "-".equals(token2.getToken())) {
      return null;
    }

    Set<String> set1 = null;
    if (token1.getReadings().size() == 1 &&
        token1.getReadings().get(0).getPOSTag() != null &&
        token1.getReadings().get(0).getPOSTag().endsWith(":STV")) {
      // catch the error in "Meiner Chef raucht."
      set1 = Collections.emptySet();
    } else {
      set1 = getAgreementCategories(token1);
    }
    if (set1 == null) {
      return null;  // word not known, assume it's correct
    }
    Set<String> set2 = getAgreementCategories(token2);
    if (set2 == null) {
      return null;
    }
    set1.retainAll(set2);
    RuleMatch ruleMatch = null;
    if (set1.isEmpty() && !isException(token1, token2)) {
      RuleMatch compoundMatch = getCompoundError(token1, token2, tokenPos, sentence);
      if (compoundMatch != null) {
        return compoundMatch;
      }
      List<String> errorCategories = getCategoriesCausingError(token1, token2);
      String errorDetails = errorCategories.isEmpty() ?
            "Kasus, Genus oder Numerus" : String.join(" und ", errorCategories);
      String msg = "Möglicherweise fehlende grammatische Übereinstimmung zwischen Artikel und Nomen " +
            "bezüglich " + errorDetails + ".";
      String shortMsg = "Möglicherweise keine Übereinstimmung bezüglich " + errorDetails;
      ruleMatch = new RuleMatch(this, sentence, token1.getStartPos(),
              token2.getEndPos(), msg, shortMsg);
      /*try {
        // this will not give a match for compounds that are not in the dictionary...
        ruleMatch.setUrl(new URL("https://www.korrekturen.de/flexion/deklination/" + token2.getToken() + "/"));
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }*/
      AgreementSuggestor suggestor = new AgreementSuggestor(language.getSynthesizer(), token1, token2);
      List<String> suggestions = suggestor.getSuggestions();
      ruleMatch.setSuggestedReplacements(suggestions);
    }
    return ruleMatch;
  }

  // z.B. "die Original Mail" -> "die Originalmail"
  @Nullable
  private RuleMatch getCompoundError(AnalyzedTokenReadings token1, AnalyzedTokenReadings token2, int tokenPos, AnalyzedSentence sentence) {
    if (tokenPos != -1 && tokenPos + 2 < sentence.getTokensWithoutWhitespace().length) {
      AnalyzedTokenReadings nextToken = sentence.getTokensWithoutWhitespace()[tokenPos + 2];
      if (StringTools.startsWithUppercase(nextToken.getToken())) {
        String potentialCompound = token2.getToken() + StringTools.lowercaseFirstChar(nextToken.getToken());
        String testPhrase = token1.getToken() + " " + potentialCompound;
        String hyphenPotentialCompound = token2.getToken() + "-" + nextToken.getToken();
        String hyphenTestPhrase = token1.getToken() + " " + hyphenPotentialCompound;
        return getRuleMatch(token1, sentence, nextToken, testPhrase, hyphenTestPhrase);
      }
    }
    return null;
  }

  // z.B. "die neue Original Mail" -> "die neue Originalmail"
  @Nullable
  private RuleMatch getCompoundError(AnalyzedTokenReadings token1, AnalyzedTokenReadings token2, AnalyzedTokenReadings token3,
                                     int tokenPos, AnalyzedSentence sentence) {
    if (tokenPos != -1 && tokenPos + 3 < sentence.getTokensWithoutWhitespace().length) {
      AnalyzedTokenReadings nextToken = sentence.getTokensWithoutWhitespace()[tokenPos + 3];
      if (StringTools.startsWithUppercase(nextToken.getToken())) {
        String potentialCompound = token3.getToken() + StringTools.lowercaseFirstChar(nextToken.getToken());
        String testPhrase = token1.getToken() + " " + token2.getToken() + " " + potentialCompound;
        String hyphenPotentialCompound = token3.getToken() + "-" + nextToken.getToken();
        String hyphenTestPhrase = token1.getToken() + " " + token2.getToken() + " " + hyphenPotentialCompound;
        return getRuleMatch(token1, sentence, nextToken, testPhrase, hyphenTestPhrase);
      }
    }
    return null;
  }

  @Nullable
  private RuleMatch getRuleMatch(AnalyzedTokenReadings token1, AnalyzedSentence sentence, AnalyzedTokenReadings nextToken, String testPhrase, String hyphenTestPhrase) {
    try {
      initLt();
      List<String> replacements = new ArrayList<>();
      if (lt.check(testPhrase).size() == 0) {
        replacements.add(testPhrase);
      }
      if (lt.check(hyphenTestPhrase).size() == 0) {
        replacements.add(hyphenTestPhrase);
      }
      if (replacements.size() > 0) {
        String message = "Wenn es sich um ein zusammengesetztes Nomen handelt, wird es zusammengeschrieben.";
        RuleMatch ruleMatch = new RuleMatch(this, sentence, token1.getStartPos(), nextToken.getEndPos(), message);
        ruleMatch.addSuggestedReplacements(replacements);
        ruleMatch.setUrl(Tools.getUrl("http://www.canoonet.eu/services/GermanSpelling/Regeln/Getrennt-zusammen/Nomen.html#Anchor-Nomen-49575"));
        return ruleMatch;
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return null;
  }
  
  private void initLt() {
    if (lt == null) {
      lt = new JLanguageTool(language);
      for (Rule rule : lt.getAllActiveRules()) {
        if (!rule.getId().equals("DE_AGREEMENT") && !rule.getId().equals("GERMAN_SPELLER_RULE")) {
          lt.disableRule(rule.getId());
        }
      }
    }
  }

  private boolean isException(AnalyzedTokenReadings token1, AnalyzedTokenReadings token2) {
    return "allen".equals(token1.getToken()) && "Grund".equals(token2.getToken());
  }

  private List<String> getCategoriesCausingError(AnalyzedTokenReadings token1, AnalyzedTokenReadings token2) {
    List<String> categories = new ArrayList<>();
    List<GrammarCategory> categoriesToCheck = Arrays.asList(GrammarCategory.KASUS, GrammarCategory.GENUS, GrammarCategory.NUMERUS);
    for (GrammarCategory category : categoriesToCheck) {
      if (agreementWithCategoryRelaxation(token1, token2, category)) {
        categories.add(category.displayName);
      }
    }
    return categories;
  }

  private RuleMatch checkDetAdjNounAgreement(AnalyzedTokenReadings token1,
      AnalyzedTokenReadings token2, AnalyzedTokenReadings token3, AnalyzedSentence sentence, int tokenPos) {
    // TODO: remove (token3 == null || token3.getToken().length() < 2)
    // see Daniel's comment from 20.12.2016 at https://github.com/languagetool-org/languagetool/issues/635
    if(token3 == null || token3.getToken().length() < 2) {
      return null;
    }
    Set<String> set = retainCommonCategories(token1, token2, token3);
    RuleMatch ruleMatch = null;
    if (set.isEmpty()) {
      RuleMatch compoundMatch = getCompoundError(token1, token2, token3, tokenPos, sentence);
      if (compoundMatch != null) {
        return compoundMatch;
      }
      // TODO: more detailed error message:
      String msg = "Möglicherweise fehlende grammatische Übereinstimmung zwischen Artikel, Adjektiv und " +
            "Nomen bezüglich Kasus, Numerus oder Genus. Beispiel: 'mein kleiner Haus' " +
            "statt 'mein kleines Haus'";
      String shortMsg = "Möglicherweise keine Übereinstimmung bezüglich Kasus, Numerus oder Genus";
      ruleMatch = new RuleMatch(this, sentence, token1.getStartPos(), token3.getEndPos(), msg, shortMsg);
    }
    return ruleMatch;
  }

  private boolean agreementWithCategoryRelaxation(AnalyzedTokenReadings token1,
                                                  AnalyzedTokenReadings token2, GrammarCategory categoryToRelax) {
    Set<GrammarCategory> categoryToRelaxSet;
    if (categoryToRelax != null) {
      categoryToRelaxSet = Collections.singleton(categoryToRelax);
    } else {
      categoryToRelaxSet = Collections.emptySet();
    }
    Set<String> set1 = getAgreementCategories(token1, categoryToRelaxSet, true);
    if (set1 == null) {
      return true;  // word not known, assume it's correct
    }
    Set<String> set2 = getAgreementCategories(token2, categoryToRelaxSet, true);
    if (set2 == null) {
      return true;
    }
    set1.retainAll(set2);
    return set1.size() > 0;
  }

  @NotNull
  private Set<String> retainCommonCategories(AnalyzedTokenReadings token1,
                                             AnalyzedTokenReadings token2, AnalyzedTokenReadings token3) {
    Set<GrammarCategory> categoryToRelaxSet = Collections.emptySet();
    Set<String> set1 = getAgreementCategories(token1, categoryToRelaxSet, true);
    if (set1 == null) {
      return Collections.emptySet();  // word not known, assume it's correct
    }
    boolean skipSol = !VIELE_WENIGE_LOWERCASE.contains(token1.getToken().toLowerCase());
    Set<String> set2 = getAgreementCategories(token2, categoryToRelaxSet, skipSol);
    if (set2 == null) {
      return Collections.emptySet();
    }
    Set<String> set3 = getAgreementCategories(token3, categoryToRelaxSet, true);
    if (set3 == null) {
      return Collections.emptySet();
    }
    set1.retainAll(set2);
    set1.retainAll(set3);
    return set1;
  }

  private Set<String> getAgreementCategories(AnalyzedTokenReadings aToken) {
    return getAgreementCategories(aToken, new HashSet<>(), false);
  }

  /** Return Kasus, Numerus, Genus of those forms with a determiner. */
  private Set<String> getAgreementCategories(AnalyzedTokenReadings aToken, Set<GrammarCategory> omit, boolean skipSol) {
    Set<String> set = new HashSet<>();
    List<AnalyzedToken> readings = aToken.getReadings();
    for (AnalyzedToken tmpReading : readings) {
      if (skipSol && tmpReading.getPOSTag() != null && tmpReading.getPOSTag().endsWith(":SOL")) {
        // SOL = alleinstehend - needs to be skipped so we find errors like "An der roter Ampel."
        continue;
      }
      AnalyzedGermanToken reading = new AnalyzedGermanToken(tmpReading);
      if (reading.getCasus() == null && reading.getNumerus() == null &&
          reading.getGenus() == null) {
        continue;
      }
      if (reading.getGenus() == GermanToken.Genus.ALLGEMEIN &&
          tmpReading.getPOSTag() != null && !tmpReading.getPOSTag().endsWith(":STV") &&  // STV: stellvertretend (!= begleitend)
          !possessiveSpecialCase(aToken, tmpReading)) {
        // genus=ALG in the original data. Not sure if this is allowed, but expand this so
        // e.g. "Ich Arbeiter" doesn't get flagged as incorrect:
        if (reading.getDetermination() == null) {
          // Nouns don't have the determination property (definite/indefinite), and as we don't want to
          // introduce a special case for that, we just pretend they always fulfill both properties:
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.MASKULINUM, GermanToken.Determination.DEFINITE, omit));
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.MASKULINUM, GermanToken.Determination.INDEFINITE, omit));
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.FEMININUM, GermanToken.Determination.DEFINITE, omit));
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.FEMININUM, GermanToken.Determination.INDEFINITE, omit));
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.NEUTRUM, GermanToken.Determination.DEFINITE, omit));
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.NEUTRUM, GermanToken.Determination.INDEFINITE, omit));
        } else {
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.MASKULINUM, reading.getDetermination(), omit));
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.FEMININUM, reading.getDetermination(), omit));
          set.add(makeString(reading.getCasus(), reading.getNumerus(), GermanToken.Genus.NEUTRUM, reading.getDetermination(), omit));
        }
      } else {
        if (reading.getDetermination() == null || "jed".equals(tmpReading.getLemma()) || "manch".equals(tmpReading.getLemma())) {  // "jeder" etc. needs a special case to avoid false alarm
          set.add(makeString(reading.getCasus(), reading.getNumerus(), reading.getGenus(), GermanToken.Determination.DEFINITE, omit));
          set.add(makeString(reading.getCasus(), reading.getNumerus(), reading.getGenus(), GermanToken.Determination.INDEFINITE, omit));
        } else {
          set.add(makeString(reading.getCasus(), reading.getNumerus(), reading.getGenus(), reading.getDetermination(), omit));
        }
      }
    }
    return set;
  }

  private boolean possessiveSpecialCase(AnalyzedTokenReadings aToken, AnalyzedToken tmpReading) {
    // would cause error misses as it contains 'ALG', e.g. in "Der Zustand meiner Gehirns."
    return aToken.hasPosTagStartingWith("PRO:POS") && StringUtils.equalsAny(tmpReading.getLemma(), "ich", "sich");
  }

  private String makeString(GermanToken.Kasus casus, GermanToken.Numerus num, GermanToken.Genus gen,
      GermanToken.Determination determination, Set<GrammarCategory> omit) {
    List<String> l = new ArrayList<>();
    if (casus != null && !omit.contains(GrammarCategory.KASUS)) {
      l.add(casus.toString());
    }
    if (num != null && !omit.contains(GrammarCategory.NUMERUS)) {
      l.add(num.toString());
    }
    if (gen != null && !omit.contains(GrammarCategory.GENUS)) {
      l.add(gen.toString());
    }
    if (determination != null) {
      l.add(determination.toString());
    }
    return String.join("/", l);
  }

}
