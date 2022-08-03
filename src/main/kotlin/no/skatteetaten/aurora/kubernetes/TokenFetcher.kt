package no.skatteetaten.aurora.kubernetes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Implement either token (blocking) og coToken (reactive) function to get the token.
 * It is important that one of these functions is overridden.
 */
interface TokenFetcher {
    fun token(audience: String? = null): String? = runBlocking { coToken(audience) }
    suspend fun coToken(audience: String? = null): String? = withContext(Dispatchers.IO) {
        token(audience)
    }
}

class PsatTokenFetcher(private val psatTokenMount: String) : TokenFetcher {
    override fun token(audience: String?): String? {
        return File("$psatTokenMount/$audience").readText().trim()
    }
}

class StringTokenFetcher(val token: String) : TokenFetcher {
    override fun token(audience: String?) = token
}

class NoopTokenFetcher : TokenFetcher {
    override fun token(audience: String?): String? {
        logger.debug("NoopTokenFetcher configured and no token sent in")
        return null
    }
}
