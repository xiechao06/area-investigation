package com.puzheng.region_investigation.store

import android.content.Context
import android.os.Environment
import com.orhanobut.logger.Logger
import com.puzheng.region_investigation.copyTo
import com.puzheng.region_investigation.model.POIType
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import nl.komponents.kovenant.then
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.*

class POITypeStore private constructor(val context: Context) {

    companion object {
        fun with(context: Context) = POITypeStore(context)

    }

    val dir: File by lazy {
        File(Environment.getExternalStoragePublicDirectory(context.packageName), "poi_types")
    }

    private fun getPOITypeDir(poiType: POIType) = File(dir, poiType.path)

    fun getPOITypeIcon(poiType: POIType) = File(getPOITypeDir(poiType), "ic.png")

    fun getPOITypeActiveIcon(poiType: POIType) = File(getPOITypeDir(poiType), "ic_active.png")

    fun fakePoiTypes() = task {
        listOf("bus" to "公交站", "exit" to "出入口", "emergency" to "急救站").forEach {
            File(dir, it.first).apply {
                mkdirs()
                File(this, "config.json").apply {
                    if (!exists()) {
                        createNewFile()
                    }
                    writeText(JSONObject().apply {
                        put("uuid", UUID.randomUUID().toString())
                        put("name", it.second)
                        val jsonArray = JSONArray()
                        jsonArray.put(JSONObject().apply field@ {
                            this@field.put("name", "字段一")
                            this@field.put("type", "STRING")
                        })
                        jsonArray.put(JSONObject().apply field@ {
                            this@field.put("name", "字段二")
                            this@field.put("type", "TEXT")
                        })
                        jsonArray.put(JSONObject().apply field@ {
                            this@field.put("name", "字段三")
                            this@field.put("type", "IMAGES")
                        })
                        jsonArray.put(JSONObject().apply field@ {
                            this@field.put("name", "字段四")
                            this@field.put("type", "VIDEO")
                        })
                        put("fields", jsonArray)
                    }.toString())
                }
                context.assets.open("icons/ic_${it.first}.png").copyTo(File(this, "ic.png"))
                context.assets.open("icons/ic_${it.first}_active.png").copyTo(File(this, "ic_active.png"))
            }
        }
    }

    val list: Promise<List<POIType>?, Exception>
        get() = task {
            dir.listFiles({ file -> file.isDirectory })?.map {
                Logger.v("${it.path}, ${it.name}")
                val configFile = it.listFiles { file, fname -> fname == "config.json" }.getOrNull(0)
                if (configFile?.exists() ?: false) {
                    try {
                        val json = JSONObject(configFile!!.readText())
                        val jsonArray = json.getJSONArray("fields")

                        POIType(json.getString("uuid"), json.getString("name"),
                                (0..jsonArray.length() - 1).map {
                                    val o = jsonArray.getJSONObject(it)
                                    POIType.Field(o.getString("name"),
                                            POIType.FieldType.valueOf(o.getString("type").toUpperCase()),
                                            null)
                                }, it.name)
                    } catch (e: JSONException) {
                        Logger.e(e.toString())
                        null
                    } catch (e: IllegalArgumentException) {
                        Logger.e(e.toString())
                        null
                    }
                } else {
                    null
                }
            }?.filter {
                it != null
            }?.map {
                it!!
            }
            // TODO if met a unzipped zip file (no corresponding dir or phased out), zip it
        }

    fun get(uuid: String) = list then {
        it?.find { it.uuid == uuid }
    }
}