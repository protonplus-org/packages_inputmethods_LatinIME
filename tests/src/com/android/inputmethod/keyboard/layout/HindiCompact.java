/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.keyboard.layout;

import static com.android.inputmethod.keyboard.layout.DevanagariLetterConstants.*;

import com.android.inputmethod.keyboard.layout.Hindi.HindiSymbols;
import com.android.inputmethod.keyboard.layout.customizer.HindiCustomizer;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKey;
import com.android.inputmethod.keyboard.layout.expected.ExpectedKeyboardBuilder;

import java.util.Locale;

/**
 * The Hindi Compact keyboard.
 */
public final class HindiCompact extends LayoutBase {
    private static final String LAYOUT_NAME = "hindi_compact";

    public HindiCompact(final Locale locale) {
        super(new HindiCompactCustomizer(locale), HindiSymbols.class, SymbolsShifted.class);
    }

    @Override
    public String getName() { return LAYOUT_NAME; }

    private static class HindiCompactCustomizer extends HindiCustomizer {
        HindiCompactCustomizer(final Locale locale) { super(locale); }

        @Override
        public ExpectedKey[] getLeftShiftKeys(final boolean isPhone) {
            return EMPTY_KEYS;
        }
    }

    @Override
    ExpectedKey[][] getCommonAlphabetLayout(boolean isPhone) { return ALPHABET_COMMON; }

    @Override
    ExpectedKey[][] getCommonAlphabetShiftLayout(boolean isPhone, final int elementId) {
        return null;
    }

    private static final ExpectedKey[][] ALPHABET_COMMON = new ExpectedKeyboardBuilder()
            .setKeysOfRow(1,
                    // U+0914: "???" DEVANAGARI LETTER AU
                    // U+094C: "???" DEVANAGARI VOWEL SIGN AU
                    // U+0967: "???" DEVANAGARI DIGIT ONE
                    key("\u0914", joinMoreKeys(moreKey(VOWEL_SIGN_AU, "\u094C"), "\u0967", "1")),
                    // U+0910: "???" DEVANAGARI LETTER AI
                    // U+0948: "???" DEVANAGARI VOWEL SIGN AI
                    // U+0968: "???" DEVANAGARI DIGIT TWO
                    key("\u0910", joinMoreKeys(moreKey(VOWEL_SIGN_AI, "\u0948"), "\u0968", "2")),
                    // U+0906: "???" DEVANAGARI LETTER AA
                    // U+093E: "???" DEVANAGARI VOWEL SIGN AA
                    // U+0969: "???" DEVANAGARI DIGIT THREE
                    key("\u0906", joinMoreKeys(moreKey(VOWEL_SIGN_AA, "\u093E"), "\u0969", "3")),
                    // U+0908: "???" DEVANAGARI LETTER II
                    // U+0940: "???" DEVANAGARI VOWEL SIGN II
                    // U+096A: "???" DEVANAGARI DIGIT FOUR
                    key("\u0908", joinMoreKeys(moreKey(VOWEL_SIGN_II, "\u0940"), "\u096A", "4")),
                    // U+090A: "???" DEVANAGARI LETTER UU
                    // U+0942: "???" DEVANAGARI VOWEL SIGN UU
                    // U+096B: "???" DEVANAGARI DIGIT FIVE
                    key("\u090A", joinMoreKeys(moreKey(VOWEL_SIGN_UU, "\u0942"), "\u096B", "5")),
                    // U+092C: "???" DEVANAGARI LETTER BA
                    // U+092D: "???" DEVANAGARI LETTER BHA
                    // U+096C: "???" DEVANAGARI DIGIT SIX
                    key("\u092C", joinMoreKeys("\u092D", "\u096C", "6")),
                    // U+0939: "???" DEVANAGARI LETTER HA
                    // U+096D: "???" DEVANAGARI DIGIT SEVEN
                    key("\u0939", joinMoreKeys("\u096D", "7")),
                    // U+0917: "???" DEVANAGARI LETTER GA
                    // U+0918: "???" DEVANAGARI LETTER GHA
                    // U+096E: "???" DEVANAGARI DIGIT EIGHT
                    key("\u0917", joinMoreKeys("\u0918", "\u096E", "8")),
                    // U+0926: "???" DEVANAGARI LETTER DA
                    // U+0927: "???" DEVANAGARI LETTER DHA
                    // U+096F: "???" DEVANAGARI DIGIT NINE
                    key("\u0926", joinMoreKeys("\u0927", "\u096F", "9")),
                    // U+091C: "???" DEVANAGARI LETTER JA
                    // U+091D: "???" DEVANAGARI LETTER JHA
                    // U+091C/U+094D/U+091E:
                    //     "?????????" DEVANAGARI LETTER JA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER NYA
                    // U+0966: "???" DEVANAGARI DIGIT ZERO
                    key("\u091C", joinMoreKeys("\u091D", "\u091C\u094D\u091E", "\u0966", "0")),
                    // U+0921: "???" DEVANAGARI LETTER DDA
                    // U+0922: "???" DEVANAGARI LETTER DDHA
                    key("\u0921", moreKey("\u0922")))
            .setKeysOfRow(2,
                    // U+0913: "???" DEVANAGARI LETTER O
                    // U+094B: "???" DEVANAGARI VOWEL SIGN O
                    key("\u0913", moreKey(VOWEL_SIGN_O, "\u094B")),
                    // U+090F: "???" DEVANAGARI LETTER E
                    // U+0947: "???" DEVANAGARI VOWEL SIGN E
                    key("\u090F", moreKey(VOWEL_SIGN_E, "\u0947")),
                    // U+0905: "???" DEVANAGARI LETTER A
                    // U+094D: "???" DEVANAGARI SIGN VIRAMA
                    key("\u0905", moreKey(SIGN_VIRAMA, "\u094D")),
                    // U+0907: "???" DEVANAGARI LETTER I
                    // U+093F: "???" DEVANAGARI VOWEL SIGN I
                    key("\u0907", moreKey(VOWEL_SIGN_I, "\u093F")),
                    // U+0909: "???" DEVANAGARI LETTER U
                    // U+0941: "???" DEVANAGARI VOWEL SIGN U
                    key("\u0909", moreKey(VOWEL_SIGN_U, "\u0941")),
                    // U+092A: "???" DEVANAGARI LETTER PA
                    // U+092B: "???" DEVANAGARI LETTER PHA
                    key("\u092A", moreKey("\u092B")),
                    // U+0930: "???" DEVANAGARI LETTER RA
                    // U+090B: "???" DEVANAGARI LETTER VOCALIC R
                    // U+0943: "???" DEVANAGARI VOWEL SIGN VOCALIC R
                    key("\u0930", joinMoreKeys("\u090B", moreKey(VOWEL_SIGN_VOCALIC_R, "\u0943"))),
                    // U+0915: "???" DEVANAGARI LETTER KA
                    // U+0916: "???" DEVANAGARI LETTER KHA
                    key("\u0915", moreKey("\u0916")),
                    // U+0924: "???" DEVANAGARI LETTER TA
                    // U+0925: "???" DEVANAGARI LETTER THA
                    // U+0924/U+094D/U+0930:
                    //     "?????????" DEVANAGARI LETTER TA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER RA
                    key("\u0924", joinMoreKeys("\u0925", "\u0924\u094D\u0930")),
                    // U+091A: "???" DEVANAGARI LETTER CA
                    // U+091B: "???" DEVANAGARI LETTER CHA
                    key("\u091A", moreKey("\u091B")),
                    // U+091F: "???" DEVANAGARI LETTER TTA
                    // U+0920: "???" DEVANAGARI LETTER TTHA
                    key("\u091F", moreKey("\u0920")))
            .setKeysOfRow(3,
                    // U+0911: "???" DEVANAGARI LETTER CANDRA O
                    // U+0949: "???" DEVANAGARI VOWEL SIGN CANDRA O
                    key("\u0911", moreKey(VOWEL_SIGN_CANDRA_O, "\u0949")),
                    // U+094D: "???" DEVANAGARI SIGN VIRAMA
                    // U+0945: "???" DEVANAGARI VOWEL SIGN CANDRA E
                    // U+090D: "???" DEVANAGARI LETTER CANDRA E
                    key(SIGN_VIRAMA, "\u094D", joinMoreKeys(
                            moreKey(VOWEL_SIGN_CANDRA_E, "\u0945"), "\u090D")),
                    // U+0902: "???" DEVANAGARI SIGN ANUSVARA
                    // U+0903: "??????" DEVANAGARI SIGN VISARGA
                    // U+0901: "???" DEVANAGARI SIGN CANDRABINDU
                    // U+093C: "???" DEVANAGARI SIGN NUKTA
                    key(SIGN_ANUSVARA, "\u0902", joinMoreKeys(
                            moreKey(SIGN_VISARGA, "\u0903"),
                            moreKey(SIGN_CANDRABINDU, "\u0901"),
                            moreKey(SIGN_NUKTA, "\u093C"))),
                    // U+092E: "???" DEVANAGARI LETTER MA
                    // U+0950: "???" DEVANAGARI OM
                    key("\u092E", moreKey("\u0950")),
                    // U+0928: "???" DEVANAGARI LETTER NA
                    // U+0923: "???" DEVANAGARI LETTER NNA
                    // U+091E: "???" DEVANAGARI LETTER NYA
                    // U+0919: "???" DEVANAGARI LETTER NGA
                    key("\u0928", joinMoreKeys("\u0923", "\u091E", "\u0919")),
                    // U+0935: "???" DEVANAGARI LETTER VA
                    // U+0932: "???" DEVANAGARI LETTER LA
                    "\u0935", "\u0932",
                    // U+0938: "???" DEVANAGARI LETTER SA
                    // U+0936: "???" DEVANAGARI LETTER SHA
                    // U+0937: "???" DEVANAGARI LETTER SSA
                    // U+0936/U+094D/U+0930:
                    //     "?????????" DEVANAGARI LETTER SHA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER RA
                    key("\u0938", joinMoreKeys("\u0936", "\u0937", "\u0936\u094D\u0930")),
                    // U+092F: "???" DEVANAGARI LETTER YA
                    // U+0915/U+094D/U+0937:
                    //     "?????????" DEVANAGARI LETTER KA/DEVANAGARI SIGN VIRAMA/DEVANAGARI LETTER SSA
                    "\u092F", "\u0915\u094D\u0937")
            .build();
}
