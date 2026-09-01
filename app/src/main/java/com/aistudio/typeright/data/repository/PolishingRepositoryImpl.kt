package com.aistudio.typeright.data.repository

import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.model.ToneStyle
import com.aistudio.typeright.domain.repository.PolishingRepository
import com.aistudio.typeright.domain.repository.TextComparison
import com.aistudio.typeright.domain.repository.TextChange
import com.aistudio.typeright.domain.repository.ChangeType
import javax.inject.Inject

/**
 * Implementation of polishing repository with tone transformation
 */
class PolishingRepositoryImpl @Inject constructor() : PolishingRepository {
    
    override suspend fun transformTone(
        text: String,
        style: ToneStyle
    ): Result<String> = try {
        val transformed = when (style) {
            ToneStyle.PROFESSIONAL -> makeProfessional(text)
            ToneStyle.CASUAL -> makeCasual(text)
            ToneStyle.CONCISE -> makeConcise(text)
            ToneStyle.FRIENDLY -> makeFriendly(text)
            ToneStyle.ACADEMIC -> makeAcademic(text)
            ToneStyle.EXPRESSIVE -> makeExpressive(text)
        }
        Result.Success(transformed)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun proofread(text: String): Result<String> = try {
        var result = text
        // Fix common punctuation issues
        result = result.replace(Regex("\\s+\\."), ".")
        result = result.replace(Regex("\\s+,"), ",")
        result = result.replace(Regex("\\s+!"), "!")
        result = result.replace(Regex("\\s+\\?"), "?")
        // Capitalize sentence beginnings
        result = capitalizeProper(result)
        Result.Success(result)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun cleanupVoiceNotes(text: String): Result<String> = try {
        var result = text
        // Remove filler words
        result = result.replace(Regex("\\b(um|uh|like|you know|actually|basically)\\b", RegexOption.IGNORE_CASE), "")
        // Clean up spacing
        result = result.replace(Regex("\\s+"), " ").trim()
        // Proofread
        result = when (val proof = proofread(result)) {
            is Result.Success -> proof.data
            else -> result
        }
        Result.Success(result)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun getComparison(
        original: String,
        transformed: String
    ): Result<TextComparison> = try {
        val changes = calculateChanges(original, transformed)
        Result.Success(TextComparison(original, transformed, changes))
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    private fun makeProfessional(text: String): String {
        val replacements = mapOf(
            Regex("\\byou guys\\b", RegexOption.IGNORE_CASE) to "you",
            Regex("\\bgonna\\b", RegexOption.IGNORE_CASE) to "going to",
            Regex("\\bwanna\\b", RegexOption.IGNORE_CASE) to "want to",
            Regex("\\bcuz\\b", RegexOption.IGNORE_CASE) to "because",
            Regex("\\nlol\\n", RegexOption.IGNORE_CASE) to ""
        )
        var result = text
        replacements.forEach { (pattern, replacement) ->
            result = result.replace(pattern, replacement)
        }
        return capitalizeProper(result)
    }
    
    private fun makeCasual(text: String): String {
        // Add more relaxed phrasing
        var result = text.replace(Regex("\\bI think\\b", RegexOption.IGNORE_CASE), "I reckon")
        result = result.replace(Regex("\\bso\\b", RegexOption.IGNORE_CASE), "so like")
        return result
    }
    
    private fun makeConcise(text: String): String {
        val replacements = mapOf(
            Regex("\\bI think that\\b", RegexOption.IGNORE_CASE) to "I think",
            Regex("\\bat this point in time\\b", RegexOption.IGNORE_CASE) to "now",
            Regex("\\bdue to the fact that\\b", RegexOption.IGNORE_CASE) to "because",
            Regex("\\bhas been able to\\b", RegexOption.IGNORE_CASE) to "can"
        )
        var result = text
        replacements.forEach { (pattern, replacement) ->
            result = result.replace(pattern, replacement)
        }
        return result
    }
    
    private fun makeFriendly(text: String): String {
        var result = text
        result = result.replace(Regex("\.(?!$)", RegexOption.MULTILINE), "! ")
        result = result.replace(Regex("([!?])(?!$)", RegexOption.MULTILINE)) { "${it.value} :) " }
        return result
    }
    
    private fun makeAcademic(text: String): String {
        val replacements = mapOf(
            Regex("\\bget\\b", RegexOption.IGNORE_CASE) to "obtain",
            Regex("\\bthing\\b", RegexOption.IGNORE_CASE) to "phenomenon",
            Regex("\\bstuff\\b", RegexOption.IGNORE_CASE) to "subject matter",
            Regex("\\bso\\b", RegexOption.IGNORE_CASE) to "consequently"
        )
        var result = text
        replacements.forEach { (pattern, replacement) ->
            result = result.replace(pattern, replacement)
        }
        return result
    }
    
    private fun makeExpressive(text: String): String {
        var result = text
        result = result.replace(Regex("([.!?])$"), "${"$1".repeat(2)}")
        return result
    }
    
    private fun capitalizeProper(text: String): String {
        return text.split("\\b(?<=[.!?])\\s+".toRegex()).joinToString(" ") { sentence ->
            if (sentence.isNotEmpty()) {
                sentence[0].uppercase() + sentence.substring(1)
            } else {
                sentence
            }
        }
    }
    
    private fun calculateChanges(original: String, transformed: String): List<TextChange> {
        // Simple change detection
        return if (original != transformed) {
            listOf(
                TextChange(
                    original = original,
                    transformed = transformed,
                    startIndex = 0,
                    endIndex = original.length,
                    changeType = ChangeType.REPLACED
                )
            )
        } else {
            emptyList()
        }
    }
}
