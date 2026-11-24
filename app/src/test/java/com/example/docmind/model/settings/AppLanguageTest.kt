package com.example.docmind.model.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun emptyTagsFollowSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags(""))
    }

    @Test
    fun supportedLanguageTagsAreRecognized() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromLanguageTags("vi"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en"))
    }

    @Test
    fun regionalTagsUseTheirLanguageSubtag() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromLanguageTags("vi-VN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-US"))
    }

    @Test
    fun tagsAreCaseInsensitive() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromLanguageTags("VI-vn"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("EN-us"))
    }

    @Test
    fun onlyFirstLanguageInListDeterminesSelection() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-US,vi-VN"))
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromLanguageTags("vi-VN,en-US"))
    }

    @Test
    fun unknownFirstLanguageDoesNotFallThroughToSupportedSecondLanguage() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("fr-FR,en-US"))
    }

    @Test
    fun unsupportedLanguagesFollowSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("fr"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("de-DE"))
    }

    @Test
    fun similarLanguageNamesDoNotMatchSupportedLanguages() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("english"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("vietnamese"))
    }

    @Test
    fun optionsExposeExactLocaleTags() {
        assertEquals("", AppLanguage.SYSTEM.languageTag)
        assertEquals("vi", AppLanguage.VIETNAMESE.languageTag)
        assertEquals("en", AppLanguage.ENGLISH.languageTag)
    }
}
