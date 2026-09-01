package com.aistudio.typeright.test

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertTrue
import com.aistudio.typeright.domain.model.Result

/**
 * Unit tests for result handling
 */
class ResultTest {
    
    @Test
    fun testResultSuccess() {
        val result = Result.Success(42)
        val mapped = result.map { it * 2 }
        assertTrue(mapped is Result.Success)
    }
    
    @Test
    fun testResultError() {
        val exception = Exception("Test error")
        val result: Result<Int> = Result.Error(exception)
        val mapped = result.map { it * 2 }
        assertTrue(mapped is Result.Error)
    }
}
