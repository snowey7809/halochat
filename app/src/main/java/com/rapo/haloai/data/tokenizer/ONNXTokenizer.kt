package com.rapo.haloai.data.tokenizer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.FileReader

class ONNXTokenizer(vocabPath: String) {

    private val vocab: Map<String, Int>
    private val reverseVocab: Map<Int, String>
    val eosTokenId: Int

    init {
        val type = object : TypeToken<Map<String, Int>>() {}.type
        vocab = Gson().fromJson(FileReader(vocabPath), type)
        reverseVocab = vocab.entries.associate { (key, value) -> value to key }
        eosTokenId = vocab["<|end|>"] ?: -1
    }

    fun encode(text: String): IntArray {
        // This is a simplified BPE tokenizer based on the vocab
        // In a real-world scenario, you would use a more robust implementation
        val tokens = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            var bestMatch = ""
            var bestMatchId = -1
            for (j in i until text.length) {
                val substring = text.substring(i, j + 1)
                if (vocab.containsKey(substring)) {
                    bestMatch = substring
                    bestMatchId = vocab[substring]!!
                } else if (bestMatch.isNotEmpty()) {
                    break
                }
            }

            if (bestMatch.isNotEmpty()) {
                tokens.add(bestMatchId)
                i += bestMatch.length
            } else {
                // Handle unknown characters
                i++
            }
        }
        return tokens.toIntArray()
    }

    fun decode(tokenIds: IntArray): String {
        return tokenIds.asList().mapNotNull { reverseVocab[it] }.joinToString("")
    }
}
