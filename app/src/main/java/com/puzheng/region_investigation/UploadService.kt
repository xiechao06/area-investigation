package com.puzheng.region_investigation

import android.app.Activity
import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.*
import android.support.v4.content.LocalBroadcastManager
import android.widget.Toast
import com.orhanobut.logger.Logger
import com.puzheng.region_investigation.model.UploadTask
import com.puzheng.region_investigation.store.AccountStore
import com.puzheng.region_investigation.store.RegionStore
import com.puzheng.region_investigation.store.UploadTaskStore
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.task
import nl.komponents.kovenant.then
import nl.komponents.kovenant.ui.successUi
import okhttp3.*
import okio.BufferedSink
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * An [IntentService] subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 *
 *
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
class UploadService : IntentService("UploadIntentService") {

    private val binder: LocalBinder by lazy {
        LocalBinder()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    inner class LocalBinder : Binder() {
        val service: UploadService by lazy {
            this@UploadService
        }
    }

    private val dir = File(Environment.getExternalStoragePublicDirectory(MyApplication.context.packageName),
            ".cache").apply {
        if (!exists()) {
        }
    }

    private val dbHelper: DBHelper by lazy {
        DBHelper(baseContext)
    }

    private val regionStore: RegionStore by lazy {
        RegionStore.with(baseContext)
    }

    private val accountStore: AccountStore by lazy {
        AccountStore.with(baseContext)
    }

    private val handler: Handler by lazy {
        val thread = HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                Logger.v("uploading will begin: ${msg.toString()}")
                val task = (msg.obj as UploadTask)

                try {
                    val region = regionStore.getSync(task.regionId) ?: return
                    val jsonObject = JSONObject().apply {
                        region.jsonizeSync(this)
                    }
                    val zipFile = File(dir, "${task.regionId}.zip")
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).apply {
                        putNextEntry(ZipEntry("region.json"))
                        write(jsonObject.toString().toByteArray())
                        closeEntry()
                        region.poiListSync?.forEach {
                            poi ->
                            addDir(poi.dir, "pois")
                        }
                        close()
                    }
                    var sent = 0L
                    val progressingRequestBody = object : RequestBody() {
                        override fun contentType(): MediaType? = MediaType.parse("application/zip")

                        override fun contentLength() = zipFile.length()

                        override fun writeTo(sink: BufferedSink) {
                            val buf = ByteArray(4096)
                            val src = BufferedInputStream(FileInputStream(zipFile))
                            while (true) {
                                val count = src.read(buf, 0, buf.size)
                                if (count == -1) {
                                    break
                                }
                                sent += count
                                sink.write(buf, 0, count)
                                LocalBroadcastManager.getInstance(this@UploadService).sendBroadcast(Intent(PROGRESS).apply {
                                    putExtra("sent", sent)
                                    putExtra("total", zipFile.length())
                                    putExtra("regionId", task.regionId)
                                    putExtra("resultCode", Activity.RESULT_OK)
                                })
                            }
                            src.close()
                        }
                    }
                    val body = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("username", accountStore.account?.username)
                            .addFormDataPart("orgCode", accountStore.account?.orgCode)
                            .addFormDataPart("zip", zipFile.name, progressingRequestBody).build()
                    val response = OkHttpClient().newCall(
                            Request.Builder()
                                    .url(ConfigUtil.with(baseContext).uploadBackend)
                                    .post(body)
                                    .build()).execute()
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected code " + response)
                    }
                    response.body().close()
                    Logger.v("upload done")
                    task.isFinished = true
                    region.setSyncedSync()
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(baseContext, R.string.upload_task_completed, Toast.LENGTH_SHORT).show()
                    }
                } catch(e: Exception) {
                    e.printStackTrace();
                } finally {

                }
            }
        }
    }

    private val uploadTaskStore: UploadTaskStore by lazy {
        UploadTaskStore.with(baseContext)
    }


    val uploadList: Promise<List<UploadTask>?, Exception>
        get() = uploadTaskStore.list

    override fun onCreate() {
        super.onCreate()
        Logger.v("onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.v("started with $startId")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            if (ACTION_FOO == action) {
                val param1 = intent.getStringExtra(EXTRA_PARAM1)
                val param2 = intent.getStringExtra(EXTRA_PARAM2)
                handleActionFoo(param1, param2)
            } else if (ACTION_BAZ == action) {
                val param1 = intent.getStringExtra(EXTRA_PARAM1)
                val param2 = intent.getStringExtra(EXTRA_PARAM2)
                handleActionBaz(param1, param2)
            }
        }
    }

    fun upload(regionIds: List<Long>) {
        Logger.v("upload regions: $regionIds")
        // save in db

        uploadTaskStore.list then {
            tasks ->
            dbHelper.withWritableDb {
                db ->
                regionIds.forEach {
                    regionId ->
                    if (tasks == null || tasks.all {
                        it.regionId != regionId
                    }) {
                        handler.sendMessage(handler.obtainMessage(UPLOAD_REGION, uploadTaskStore.newSync(regionId)))
                    } else {
                        Logger.v("region $regionId is uploading, so it won't be uploaded")
                    }
                }
            }
        } successUi {
            toast(R.string.add_to_upload_list)
        }
    }


    /**
     * Handle action Foo in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionFoo(param1: String, param2: String) {
        // TODO: Handle action Foo
        throw UnsupportedOperationException("Not yet implemented")
    }

    /**
     * Handle action Baz in the provided background thread with the provided
     * parameters.
     */
    private fun handleActionBaz(param1: String, param2: String) {
        // TODO: Handle action Baz
        throw UnsupportedOperationException("Not yet implemented")
    }

    companion object {
        val UPLOAD_REGION = uniqueId()
        const val PROGRESS = "com.puzheng.region_investigation.UploadIntentService.PROGRESS"

        // TODO: Rename actions, choose action names that describe tasks that this
        // IntentService can perform, e.g. ACTION_FETCH_NEW_ITEMS
        private val ACTION_FOO = "com.puzheng.region_investigation.action.FOO"
        private val ACTION_BAZ = "com.puzheng.region_investigation.action.BAZ"

        // TODO: Rename parameters
        private val EXTRA_PARAM1 = "com.puzheng.region_investigation.extra.PARAM1"
        private val EXTRA_PARAM2 = "com.puzheng.region_investigation.extra.PARAM2"

        /**
         * Starts this service to perform action Foo with the given parameters. If
         * the service is already performing a task this action will be queued.

         * @see IntentService
         */
        // TODO: Customize helper method
        fun startActionFoo(context: Context, param1: String, param2: String) {
            val intent = Intent(context, UploadService::class.java)
            intent.action = ACTION_FOO
            intent.putExtra(EXTRA_PARAM1, param1)
            intent.putExtra(EXTRA_PARAM2, param2)
            context.startService(intent)
        }

        /**
         * Starts this service to perform action Baz with the given parameters. If
         * the service is already performing a task this action will be queued.

         * @see IntentService
         */
        // TODO: Customize helper method
        fun startActionBaz(context: Context, param1: String, param2: String) {
            val intent = Intent(context, UploadService::class.java)
            intent.action = ACTION_BAZ
            intent.putExtra(EXTRA_PARAM1, param1)
            intent.putExtra(EXTRA_PARAM2, param2)
            context.startService(intent)
        }
    }

    // MR extensions

    /**
     * add all files under a directory into the zip, with prefix prepended to each file
     */
    private fun ZipOutputStream.addDir(dir: File, prefix: String = "") {
        val buf = ByteArray(4096)
        dir.listFiles().forEach {
            when {
                it.isFile -> {
                    putNextEntry(ZipEntry(File(prefix, it.relativeTo(dir).path).path.apply {
                        Logger.v("put entry $this into zip")
                    }))
                    try {
                        BufferedInputStream(FileInputStream(it)).let {
                            src ->
                            while (true) {
                                val count = src.read(buf, 0, buf.size)
                                if (count == -1) {
                                    break
                                }
                                write(buf, 0, count)
                            }
                            src.close()
                        }
                    } catch(e: Exception) {
                        e.printStackTrace()
                    }
                    closeEntry()
                }
                it.isDirectory -> {
                    addDir(File(dir, it.name), File(prefix, it.name).path)
                }
            }
        }
    }
}