package com.example.bilibili.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BilibiliAccountStore(context: Context) {
    private val file = File(context.filesDir, "bilibili_accounts.json")

    @Synchronized
    fun readAccounts(): List<StoredBilibiliAccount> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            root.optJSONArray("accounts").toStoredAccounts()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun readActiveAccountId(): String? =
        runCatching {
            if (!file.exists()) return null
            JSONObject(file.readText(Charsets.UTF_8)).optString("active_id").takeIf { it.isNotBlank() }
        }.getOrNull()

    @Synchronized
    fun getActiveAccount(): StoredBilibiliAccount? {
        val activeId = readActiveAccountId() ?: return null
        return readAccounts().firstOrNull { it.uid == activeId }
    }

    @Synchronized
    fun upsertAccount(account: StoredBilibiliAccount) {
        val accounts = readAccounts().filterNot { it.uid == account.uid } + account
        write(accounts, account.uid)
    }

    @Synchronized
    fun setActiveAccountId(id: String?) {
        write(readAccounts(), id)
    }

    @Synchronized
    fun removeAccount(id: String) {
        val accounts = readAccounts().filterNot { it.uid == id }
        val activeId = readActiveAccountId()
        val nextActive = if (activeId != id) activeId else accounts.firstOrNull()?.uid
        write(accounts, nextActive)
    }

    @Synchronized
    private fun write(accounts: List<StoredBilibiliAccount>, activeId: String?) {
        val root = JSONObject()
            .put("active_id", activeId.orEmpty())
            .put("accounts", accounts.toJsonArray())
        file.parentFile?.mkdirs()
        file.writeText(root.toString(), Charsets.UTF_8)
    }

    private fun JSONArray?.toStoredAccounts(): List<StoredBilibiliAccount> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val uid = item.optString("uid").takeIf { it.isNotBlank() } ?: continue
                val credentialJson = item.optJSONObject("credential") ?: continue
                val credential = BilibiliCredential(
                    dedeUserId = credentialJson.optString("dede_user_id"),
                    sessdata = credentialJson.optString("sessdata"),
                    biliJct = credentialJson.optString("bili_jct"),
                    buvid3 = credentialJson.optString("buvid3"),
                    buvid4 = credentialJson.optString("buvid4"),
                    accessKey = credentialJson.optString("access_key"),
                    refreshToken = credentialJson.optString("refresh_token"),
                )
                add(
                    StoredBilibiliAccount(
                        uid = uid,
                        name = item.optString("name"),
                        face = item.optNullableString("face"),
                        credential = credential,
                    ),
                )
            }
        }
    }

    private fun List<StoredBilibiliAccount>.toJsonArray(): JSONArray =
        JSONArray().also { array ->
            forEach { account ->
                array.put(
                    JSONObject()
                        .put("uid", account.uid)
                        .put("name", account.name)
                        .put("face", account.face)
                        .put(
                            "credential",
                            JSONObject()
                                .put("dede_user_id", account.credential.dedeUserId)
                                .put("sessdata", account.credential.sessdata)
                                .put("bili_jct", account.credential.biliJct)
                                .put("buvid3", account.credential.buvid3)
                                .put("buvid4", account.credential.buvid4)
                                .put("access_key", account.credential.accessKey)
                                .put("refresh_token", account.credential.refreshToken),
                        ),
                )
            }
        }

    private fun JSONObject.optNullableString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}
