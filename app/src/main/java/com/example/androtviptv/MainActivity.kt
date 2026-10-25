package com.example.androtviptv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlin.concurrent.thread

private data class PlaylistSource(
    val id: String, val name: String, val mode: String, val address: String,
    val username: String = "", val password: String = ""
)

private data class MediaItem(
    val id: String, val name: String, val url: String, val group: String,
    val type: String, val icon: String = "", val extension: String = "", val seriesId: String = "",
    val sourceId: String = ""
) {
    val favoriteKey: String get() = "$type:$id:$url"
}

class MainActivity : Activity() {
    private val items = mutableListOf<MediaItem>()
    private val savedSources = mutableListOf<PlaylistSource>()
    private val favorites = mutableSetOf<String>()
    private val recent = mutableListOf<MediaItem>()
    private var selectedGroup = "Tüm kategoriler"
    private lateinit var tabBar: LinearLayout
    private lateinit var categoryBar: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var sourceName: EditText
    private lateinit var playlistUrl: EditText
    private lateinit var server: EditText
    private lateinit var username: EditText
    private lateinit var password: EditText
    private lateinit var sourceMode: RadioGroup
    private var selectedType = "Canlı TV"
    private val bg = Color.rgb(7, 10, 17)
    private val panel = Color.rgb(20, 26, 39)
    private val accent = Color.rgb(222, 181, 91)
    private val muted = Color.rgb(157, 169, 188)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        favorites.addAll(getSharedPreferences("androtv", MODE_PRIVATE).getStringSet("favorites", emptySet()) ?: emptySet())
        try {
            val saved = JSONArray(getSharedPreferences("androtv", MODE_PRIVATE).getString("recent", "[]"))
            for (i in 0 until saved.length()) {
                val o = saved.optJSONObject(i) ?: continue
                recent.add(MediaItem(o.optString("id"), o.optString("name"), o.optString("url"), o.optString("group"), o.optString("type"), o.optString("icon"), o.optString("extension"), o.optString("seriesId"), o.optString("sourceId")))
            }
        } catch (_: Exception) { }
        loadSavedSources()
        buildUi()
        if (savedSources.isNotEmpty()) {
            status.text = "${savedSources.size} kayıtlı kaynak yeniden yükleniyor…"
            savedSources.toList().forEach { loadSource(it) }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 18, 28, 16); setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text = "KAISER"; textSize = 30f; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD); letterSpacing = 0.06f })
        header.addView(TextView(this).apply { text = "TV"; textSize = 30f; setTextColor(accent); setTypeface(null, android.graphics.Typeface.BOLD) })
        header.addView(TextView(this).apply { text = "   SİNEMA  •  DİZİ  •  CANLI TV"; textSize = 11f; setTextColor(muted) }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(makeButton("＋ Kaynak ekle", true) { showConnectionDialog() }, LinearLayout.LayoutParams(-2, 42).apply { setMargins(0, 0, 8, 0) })
        header.addView(makeButton("Kaynaklar", false) { showSourcesDialog() }, LinearLayout.LayoutParams(-2, 42))
        root.addView(header)
        root.addView(TextView(this).apply { text = "Kendi eğlence dünyan. İzlemeye devam et, favorilerini keşfet."; textSize = 14f; setTextColor(muted); setPadding(0, 3, 0, 14) })

        status = TextView(this).apply { text = "Kendi M3U listen veya Xtream hesap bilgilerinle bağlan."; textSize = 13f; setTextColor(muted); setPadding(0, 3, 0, 8) }
        root.addView(status)
        tabBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(tabBar)
        categoryBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 4, 0, 2) }
        root.addView(ScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(categoryBar) }, LinearLayout.LayoutParams(-1, 44))
        val scroll = ScrollView(this).apply { isFillViewport = true; isFocusable = false }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 10, 0, 8) }
        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root); render()
    }

    private fun showConnectionDialog() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 4); setBackgroundColor(bg) }
        sourceMode = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val m3u = RadioButton(this).apply { id = View.generateViewId(); text = "M3U / M3U8"; setTextColor(Color.WHITE); isChecked = true }
        val xtream = RadioButton(this).apply { id = View.generateViewId(); text = "Xtream Codes"; setTextColor(Color.WHITE) }
        sourceMode.addView(m3u); sourceMode.addView(xtream); form.addView(sourceMode)
        sourceName = edit("Kaynak adı (örn. Spor / Filmler)", false).apply { setText("Kaynak ${savedSources.size + 1}") }
        playlistUrl = edit("M3U / M3U8 oynatma listesi URL'si", false)
        server = edit("Sunucu adresi (https://...)", false)
        username = edit("Kullanıcı adı", false); password = edit("Şifre", true)
        form.addView(sourceName, fieldParams()); form.addView(playlistUrl, fieldParams()); form.addView(server, fieldParams())
        val creds = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        creds.addView(username, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(0, 3, 6, 3) })
        creds.addView(password, LinearLayout.LayoutParams(0, 48, 1f).apply { setMargins(6, 3, 0, 3) })
        form.addView(creds)
        sourceMode.setOnCheckedChangeListener { _, checked ->
            val useXtream = checked == xtream.id
            playlistUrl.visibility = if (useXtream) View.GONE else View.VISIBLE
            server.visibility = if (useXtream) View.VISIBLE else View.GONE
            creds.visibility = if (useXtream) View.VISIBLE else View.GONE
        }
        server.visibility = View.GONE; creds.visibility = View.GONE
        val dialog = AlertDialog.Builder(this).setTitle("İzleme kaynağı ekle")
            .setMessage("Kaynak adı ver; bağlantı bilgileri bu cihazda saklanır ve sonraki açılışta yeniden yüklenir.")
            .setView(form).setNegativeButton("İptal", null).setPositiveButton("BAĞLAN", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val useXtream = sourceMode.checkedRadioButtonId == xtream.id
                if (sourceName.text.isNullOrBlank()) {
                    Toast.makeText(this, "Kaynağa bir ad ver.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                }
                if (useXtream) {
                    if (!(server.text.toString().trim().startsWith("http://") || server.text.toString().trim().startsWith("https://")) || username.text.isNullOrBlank() || password.text.isNullOrBlank()) {
                        Toast.makeText(this, "Sunucu, kullanıcı adı ve şifreyi doldur.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                    }
                    dialog.dismiss(); loadXtream()
                } else {
                    val address = playlistUrl.text.toString().trim()
                    if (!address.startsWith("http://") && !address.startsWith("https://")) {
                        Toast.makeText(this, "Geçerli bir M3U/M3U8 URL'si gir.", Toast.LENGTH_LONG).show(); return@setOnClickListener
                    }
                    dialog.dismiss(); loadM3u(address)
                }
            }
        }
        dialog.show()
    }

    private fun edit(hintText: String, secret: Boolean): EditText = EditText(this).apply {
        hint = hintText; textSize = 14f; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(muted)
        setPadding(12, 0, 12, 0); setBackgroundColor(panel)
        if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
    private fun fieldParams() = LinearLayout.LayoutParams(-1, 48).apply { setMargins(0, 3, 0, 3) }
    private fun makeButton(label: String, primary: Boolean, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 12f; isAllCaps = false; isFocusable = true; isFocusableInTouchMode = true
        setPadding(14, 0, 14, 0)
        setTextColor(if (primary) Color.rgb(12, 14, 19) else Color.WHITE)
        background = roundedDrawable(if (primary) accent else panel, 12, if (primary) Color.rgb(246, 218, 151) else Color.rgb(44, 52, 68))
        stateListAnimator = null
        setOnFocusChangeListener { v, hasFocus ->
            v.background = roundedDrawable(if (primary) accent else if (hasFocus) Color.rgb(39, 47, 63) else panel, 12,
                if (hasFocus) Color.rgb(246, 218, 151) else if (primary) accent else Color.rgb(44, 52, 68))
            v.scaleX = if (hasFocus) 1.035f else 1f; v.scaleY = if (hasFocus) 1.035f else 1f
        }
        setOnClickListener { action() }
    }
    private fun roundedDrawable(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * resources.displayMetrics.density
            if (strokeColor != null) setStroke((1.5f * resources.displayMetrics.density).toInt().coerceAtLeast(1), strokeColor)
        }
    private fun hideKeyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(playlistUrl.windowToken, 0) }

    private fun loadM3u(address: String, existing: PlaylistSource? = null) {
        if (!address.startsWith("http://") && !address.startsWith("https://")) { status.text = "Geçerli bir http(s) M3U URL'si gir."; return }
        val source = existing ?: PlaylistSource(java.util.UUID.randomUUID().toString(), sourceName.text.toString().trim().ifBlank { "M3U Kaynağı" }, "m3u", address)
        status.text = "${source.name} yükleniyor…"
        thread {
            try {
                val parsed = parseM3u(request(address), source.id)
                runOnUiThread {
                    if (existing == null) saveSource(source)
                    items.removeAll { it.sourceId == source.id }
                    items.addAll(parsed)
                    selectedGroup = "Tüm kategoriler"
                    status.text = "${source.name}: ${parsed.size} içerik yüklendi."
                    render()
                }
            } catch (e: Exception) { runOnUiThread { status.text = "${source.name} yüklenemedi: ${e.message ?: "bağlantı hatası"}" } }
        }
    }

    private fun loadXtream(existing: PlaylistSource? = null) {
        val base = (existing?.address ?: server.text.toString().trim()).trimEnd('/')
        val user = existing?.username ?: username.text.toString().trim()
        val pass = existing?.password ?: password.text.toString()
        if (!(base.startsWith("http://") || base.startsWith("https://")) || user.isBlank() || pass.isBlank()) {
            status.text = "Sunucu, kullanıcı adı ve şifre alanlarını doldur."; return
        }
        val source = existing ?: PlaylistSource(java.util.UUID.randomUUID().toString(), sourceName.text.toString().trim().ifBlank { "Xtream Kaynağı" }, "xtream", base, user, pass)
        status.text = "${source.name}: Xtream içerikleri yükleniyor…"
        thread {
            try {
                val u = URLEncoder.encode(user, "UTF-8"); val p = URLEncoder.encode(pass, "UTF-8")
                val api = "$base/player_api.php?username=$u&password=$p"
                val root = JSONObject(request(api))
                val userInfo = root.optJSONObject("user_info")
                if (userInfo != null && userInfo.optString("auth", "1") == "0") throw Exception("Giriş bilgileri kabul edilmedi.")
                val loaded = mutableListOf<MediaItem>()
                val categoryMaps = mapOf(
                    "get_live_streams" to loadCategoryNames(api, "get_live_categories"),
                    "get_vod_streams" to loadCategoryNames(api, "get_vod_categories"),
                    "get_series" to loadCategoryNames(api, "get_series_categories")
                )
                listOf("get_live_streams", "get_vod_streams", "get_series").forEach { action ->
                    val arr = JSONArray(request("$api&action=$action"))
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString(if (action == "get_series") "series_id" else "stream_id", "")
                        if (id.isBlank()) continue
                        val name = o.optString("name", "İsimsiz içerik")
                        val type = when (action) { "get_live_streams" -> "Canlı TV"; "get_vod_streams" -> "Filmler"; else -> "Diziler" }
                        val categoryId = o.optString("category_id")
                        val group = o.optString("category_name").ifBlank { categoryMaps[action]?.get(categoryId) ?: "Diğer" }
                        val ext = o.optString("container_extension", if (type == "Canlı TV") "ts" else "mp4")
                        val streamUrl = when (type) {
                            "Canlı TV" -> "$base/live/$u/$p/$id.$ext"
                            "Filmler" -> "$base/movie/$u/$p/$id.$ext"
                            else -> ""
                        }
                        loaded.add(MediaItem(id, name, streamUrl, group, type, o.optString(if (type == "Filmler") "stream_icon" else "cover", o.optString("stream_icon", "")), ext, if (type == "Diziler") id else "", source.id))
                    }
                }
                runOnUiThread {
                    if (existing == null) saveSource(source)
                    items.removeAll { it.sourceId == source.id }
                    items.addAll(loaded)
                    selectedGroup = "Tüm kategoriler"
                    status.text = "${source.name}: ${loaded.size} içerik yüklendi."
                    render()
                }
            } catch (e: Exception) { runOnUiThread { status.text = "${source.name} yüklenemedi: ${e.message ?: "bağlantı hatası"}" } }
        }
    }

    private fun loadSource(source: PlaylistSource) {
        if (source.mode == "xtream") loadXtream(source) else loadM3u(source.address, source)
    }

    private fun loadCategoryNames(api: String, action: String): Map<String, String> {
        return try {
            val arr = JSONArray(request("$api&action=$action"))
            buildMap {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("category_id")
                    val name = item.optString("category_name")
                    if (id.isNotBlank() && name.isNotBlank()) put(id, name)
                }
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun saveSource(source: PlaylistSource) {
        savedSources.removeAll { it.id == source.id }
        savedSources.add(source)
        persistSources()
    }

    private fun loadSavedSources() {
        try {
            val arr = JSONArray(getSharedPreferences("androtv", MODE_PRIVATE).getString("sources", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val address = o.optString("address")
                val mode = o.optString("mode", "m3u")
                if (address.isBlank()) continue
                savedSources.add(PlaylistSource(o.optString("id", java.util.UUID.randomUUID().toString()), o.optString("name", "Kaynak"), mode, address, o.optString("username"), o.optString("password")))
            }
        } catch (_: Exception) { }
    }

    private fun persistSources() {
        val arr = JSONArray()
        savedSources.forEach { s -> arr.put(JSONObject().put("id", s.id).put("name", s.name).put("mode", s.mode).put("address", s.address).put("username", s.username).put("password", s.password)) }
        getSharedPreferences("androtv", MODE_PRIVATE).edit().putString("sources", arr.toString()).apply()
    }

    private fun showSourcesDialog() {
        if (savedSources.isEmpty()) {
            AlertDialog.Builder(this).setTitle("Kayıtlı kaynaklar").setMessage("Henüz kayıtlı kaynak yok. M3U/M3U8 veya Xtream bilgilerini girip bağlandığında kaynak otomatik kaydedilir.").setPositiveButton("Tamam", null).show()
            return
        }
        val labels = savedSources.map { "${it.name}  ·  ${if (it.mode == "xtream") "Xtream Codes" else "M3U/M3U8"}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Kayıtlı kaynaklar · ${savedSources.size}").setItems(labels) { _, which ->
            val source = savedSources[which]
            AlertDialog.Builder(this).setTitle(source.name).setItems(arrayOf("Yenile", "Sil")) { _, action ->
                if (action == 0) loadSource(source)
                else AlertDialog.Builder(this).setTitle("Kaynak silinsin mi?").setMessage("${source.name} ve bu kaynaktan yüklenen içerikler kaldırılacak.").setNegativeButton("İptal", null).setPositiveButton("Sil") { _, _ ->
                    savedSources.removeAll { it.id == source.id }; items.removeAll { it.sourceId == source.id }; persistSources(); render(); status.text = "${source.name} kaldırıldı."
                }.show()
            }.setNegativeButton("Kapat", null).show()
        }.setPositiveButton("Kapat", null).show()
    }

    private fun request(address: String): String {
        val conn = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 25000; setRequestProperty("User-Agent", "AndroTVIPTV/2.0")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception("HTTP $code")
            return body
        } finally { conn.disconnect() }
    }

    private fun parseM3u(text: String, sourceId: String = ""): List<MediaItem> {
        val out = mutableListOf<MediaItem>(); var name = "İçerik"; var group = "Diğer"; var icon = ""
        text.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("#EXTINF", true)) {
                name = line.substringAfterLast(',', "İçerik").trim().ifBlank { "İçerik" }
                group = Regex("group-title=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1) ?: "Diğer"
                icon = Regex("tvg-logo=\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1) ?: ""
            } else if ((line.startsWith("http://") || line.startsWith("https://")) && line.isNotBlank()) {
                val type = classify(name, group)
                out.add(MediaItem(line, name, line, group.ifBlank { "Diğer" }, type, icon, sourceId = sourceId)); name = "İçerik"; group = "Diğer"; icon = ""
            }
        }
        return out
    }
    private fun classify(name: String, group: String): String {
        val s = "$name $group".lowercase()
        return when {
            listOf("series", "dizi", "tv show", "season", "sezon", "episode", "bölüm").any { it in s } -> "Diziler"
            listOf("movie", "film", "cinema", "vod", "4k film").any { it in s } -> "Filmler"
            else -> "Canlı TV"
        }
    }

    private fun render() {
        if (!::content.isInitialized) return
        tabBar.removeAllViews()
        listOf("Canlı TV", "Filmler", "Diziler", "Favoriler", "Son İzlenenler", "Tümü").forEach { tab ->
            tabBar.addView(makeButton(tab, selectedType == tab) { selectedType = tab; selectedGroup = "Tüm kategoriler"; render() },
                LinearLayout.LayoutParams(0, 42, 1f).apply { setMargins(2, 0, 2, 0) })
        }
        content.removeAllViews()
        val baseVisible = when (selectedType) {
            "Tümü" -> items.toList()
            "Favoriler" -> items.filter { favorites.contains(it.favoriteKey) }
            "Son İzlenenler" -> recent.toList()
            else -> items.filter { it.type == selectedType }
        }
        val groupsAvailable = baseVisible.map { it.group.ifBlank { it.type } }.distinct().sorted()
        categoryBar.removeAllViews()
        (listOf("Tüm kategoriler") + groupsAvailable).forEach { group ->
            categoryBar.addView(makeButton(if (group == selectedGroup) "• $group" else group, group == selectedGroup) {
                selectedGroup = group; render()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 38).apply { setMargins(0, 0, 6, 0) })
        }
        val visible = if (selectedGroup == "Tüm kategoriler") baseVisible else baseVisible.filter { it.group.ifBlank { it.type } == selectedGroup }
        if (visible.isEmpty()) {
            content.addView(TextView(this).apply { text = if (selectedType == "Favoriler") "Henüz favorin yok. İçeriklerin yanındaki ☆ düğmesine bas." else if (selectedType == "Son İzlenenler") "Henüz izlenen içerik yok." else "İçerik görünmüyor. Önce M3U veya Xtream bağlantısı yükle."; textSize = 17f; setTextColor(muted); setPadding(12, 26, 12, 26) }); return
        }
        val groups = visible.groupBy { if (selectedType == "Tümü" || selectedType == "Favoriler") it.type else it.group.ifBlank { selectedType } }
        groups.forEach { (groupName, entries) ->
            content.addView(TextView(this).apply { text = groupName.uppercase(); textSize = 17f; setTypeface(null, android.graphics.Typeface.BOLD); setTextColor(Color.WHITE); setPadding(4, 12, 0, 6) })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            entries.take(100).forEach { item ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(8, 8, 8, 8)
                    background = roundedDrawable(panel, 15, Color.rgb(38, 45, 60))
                    isFocusable = true; isFocusableInTouchMode = true
                    setOnFocusChangeListener { v, hasFocus ->
                        v.background = roundedDrawable(if (hasFocus) Color.rgb(31, 39, 54) else panel, 15,
                            if (hasFocus) accent else Color.rgb(38, 45, 60))
                        v.scaleX = if (hasFocus) 1.035f else 1f; v.scaleY = if (hasFocus) 1.035f else 1f
                    }
                }
                if (item.icon.startsWith("http://") || item.icon.startsWith("https://")) {
                    val poster = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; layoutParams = LinearLayout.LayoutParams(-1, 150); contentDescription = item.name }
                    card.addView(poster)
                    loadPoster(item.icon, poster)
                } else {
                    card.addView(TextView(this).apply { text = "🎬"; textSize = 32f; setTextColor(muted); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(-1, 150) })
                }
                card.addView(TextView(this).apply { text = when (item.type) { "Canlı TV" -> "▶ LIVE"; "Filmler" -> "▶ FİLM"; "Bölümler" -> "▶ BÖLÜM"; else -> "▶ DİZİ" }; textSize = 12f; setTextColor(accent); setPadding(0, 0, 0, 8) })
                card.addView(TextView(this).apply { text = item.name; textSize = 15f; setTextColor(Color.WHITE); maxLines = 2; minHeight = 42 })
                val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                actions.addView(makeButton("▶ İzle", true) { play(item) }, LinearLayout.LayoutParams(0, 38, 1f).apply { setMargins(0, 8, 4, 0) })
                actions.addView(makeButton(if (favorites.contains(item.favoriteKey)) "★" else "☆", false) {
                    if (!favorites.add(item.favoriteKey)) favorites.remove(item.favoriteKey)
                    getSharedPreferences("androtv", MODE_PRIVATE).edit().putStringSet("favorites", favorites.toSet()).apply(); render()
                }, LinearLayout.LayoutParams(48, 38).apply { setMargins(4, 8, 0, 0) })
                card.addView(actions)
                row.addView(card, LinearLayout.LayoutParams(210, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 10, 8) })
            }
            content.addView(ScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun loadPoster(address: String, target: ImageView) {
        thread {
            try {
                val conn = (URL(address).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000 }
                val bitmap = try { conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } } finally { conn.disconnect() }
                if (bitmap != null) runOnUiThread { target.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
    }

    private fun saveRecent(item: MediaItem) {
        recent.removeAll { it.favoriteKey == item.favoriteKey }
        recent.add(0, item)
        while (recent.size > 20) recent.removeAt(recent.lastIndex)
        val arr = JSONArray()
        recent.forEach { r -> arr.put(JSONObject().put("id", r.id).put("name", r.name).put("url", r.url).put("group", r.group).put("type", r.type).put("icon", r.icon).put("extension", r.extension).put("seriesId", r.seriesId).put("sourceId", r.sourceId)) }
        getSharedPreferences("androtv", MODE_PRIVATE).edit().putString("recent", arr.toString()).apply()
    }

    private fun play(item: MediaItem) {
        if (item.type == "Diziler" && item.url.isBlank()) { loadSeriesEpisodes(item); return }
        saveRecent(item)
        startActivity(Intent(this, PlayerActivity::class.java).putExtra("name", item.name).putExtra("url", item.url))
    }

    private fun loadSeriesEpisodes(series: MediaItem) {
        val source = savedSources.firstOrNull { it.id == series.sourceId && it.mode == "xtream" }
        if (source == null) {
            AlertDialog.Builder(this).setTitle(series.name).setMessage("Dizi kaynağı bulunamadı. Xtream kaynağını yeniden ekle.").setPositiveButton("Tamam", null).show(); return
        }
        val seriesBase = source.address.trimEnd('/')
        val seriesUser = source.username
        val seriesPass = source.password
        status.text = "${series.name}: sezonlar yükleniyor…"
        thread {
            try {
                val u = URLEncoder.encode(seriesUser, "UTF-8"); val p = URLEncoder.encode(seriesPass, "UTF-8")
                val url = "$seriesBase/player_api.php?username=$u&password=$p&action=get_series_info&series_id=${URLEncoder.encode(series.seriesId.ifBlank { series.id }, "UTF-8")}"
                val data = JSONObject(request(url))
                val episodes = data.optJSONObject("episodes") ?: JSONObject()
                val episodeRows = mutableListOf<Pair<String, List<MediaItem>>>()
                val keys = episodes.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = episodes.optJSONArray(key) ?: continue
                    val rows = mutableListOf<MediaItem>()
                    for (i in 0 until arr.length()) {
                        val ep = arr.optJSONObject(i) ?: continue
                        val id = ep.optString("id", ep.optString("episode_id", "")); if (id.isBlank()) continue
                        val ext = ep.optString("container_extension", "mp4")
                        val epName = ep.optString("title", ep.optString("name", "Bölüm ${i + 1}"))
                        val stream = "$seriesBase/series/$u/$p/$id.$ext"
                        rows.add(MediaItem(id, epName, stream, "${series.name} • Sezon $key", "Bölümler", ep.optString("info").takeIf { it.startsWith("http") } ?: "", ext, sourceId = source.id))
                    }
                    episodeRows.add(Pair(key, rows))
                }
                runOnUiThread {
                    status.text = "Dizi bölümleri hazır."
                    if (episodeRows.isEmpty()) { AlertDialog.Builder(this).setTitle(series.name).setMessage("Bu dizi için bölüm bulunamadı.").setPositiveButton("Tamam", null).show(); return@runOnUiThread }
                    val seasonLabels = episodeRows.map { pair -> "Sezon ${pair.first}" }
                    AlertDialog.Builder(this).setTitle(series.name).setItems(seasonLabels.toTypedArray()) { _, which ->
                        val chosen = episodeRows[which].second
                        if (chosen.isEmpty()) { Toast.makeText(this, "Bu sezonda bölüm bulunamadı.", Toast.LENGTH_SHORT).show(); return@setItems }
                        AlertDialog.Builder(this).setTitle(seasonLabels[which]).setItems(chosen.map { it.name }.toTypedArray()) { _, epIndex -> play(chosen[epIndex]) }.setNegativeButton("Geri", null).show()
                    }.setNegativeButton("Kapat", null).show()
                }
            } catch (e: Exception) { runOnUiThread { status.text = "Bölümler yüklenemedi: ${e.message ?: "bağlantı hatası"}" } }
        }
    }

}
