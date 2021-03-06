package com.puzheng.region_investigation

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.AppCompatDialogFragment
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import com.orhanobut.logger.Logger
import com.puzheng.region_investigation.model.POI
import com.puzheng.region_investigation.model.POIType
import com.puzheng.region_investigation.store.POITypeStore
import nl.komponents.kovenant.task
import nl.komponents.kovenant.then
import nl.komponents.kovenant.ui.failUi
import nl.komponents.kovenant.ui.successUi
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

class EditPOIActivity : AppCompatActivity() {

    companion object {
        const val TAG_POI = "TAG_POI"
        val REQUEST_WRITE_EXTERNAL_STORAGE = uniqueId()
        val REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_IMAGE = uniqueId()
        val REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_VIDEO = uniqueId()
        val REQUEST_READ_EXTERNAL_STORAGE = uniqueId()
        val REQUEST_CAMERA = uniqueId()
        val REQUEST_VIDEO_CAPTURE = uniqueId()
        val SELECT_IMAGE = uniqueId()
        val VIEW_CAROUSEL = uniqueId()
        val TAKE_VIDEO = uniqueId()
        private val jpegRegex = Pattern.compile(".*\\.jpe*g$", Pattern.CASE_INSENSITIVE).toRegex()
        private val mpegRegex = Pattern.compile(".*\\.mp(eg|4)$", Pattern.CASE_INSENSITIVE).toRegex()
    }

    private var poi: POI? = null
    private val poiTypeStore: POITypeStore by lazy {
        POITypeStore.with(this)
    }
    private val container: LinearLayout by lazy {
        findViewById(R.id.container) as LinearLayout
    }


    lateinit var fieldResolvers: List<FieldResolver>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init("EditPOIActivity")
        setContentView(R.layout.activity_edit_poi)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        poi = intent.getParcelableExtra<POI>(TAG_POI)
        if (poi == null) {
            poi = savedInstanceState?.getParcelable(TAG_POI)
        }
        if (poi == null && BuildConfig.DEBUG) {

            // 伪造一个信息点用于调试
            permissionHandlers[REQUEST_READ_EXTERNAL_STORAGE] = {
                val poiType = poiTypeStore.list.get()!![0]
                poi = POI(1L, poiType.name, 1L, randomHZLatLng,
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2016-03-09 12:32:23"))
                setupView()
            }
            assertPermission(Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_READ_EXTERNAL_STORAGE) successUi {
                permissionHandlers[REQUEST_READ_EXTERNAL_STORAGE]?.invoke()
            }
        } else {
            setupView()
        }
    }

    private var poiData: Map<String, Any?>? = null
    lateinit private var poiType: POIType

    private fun setupView() {
        (findViewById(R.id.textViewCreated) as TextView).text = if (poi == null) "" else
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(poi!!.created)
        (findViewById(R.id.textViewPOIId) as TextView).text = poi?.id.toString()
        poiTypeStore.get(poi!!.poiTypeName) then {
            poiType = it!!
            poiType.extractPOIRawData(poi!!) then {
                poiData = it
            } failUi {
                toast(it.toString())
            } successUi {
                (findViewById(R.id.textViewPOIType) as TextView).text = poiType.name
                fieldResolvers = poiType.fields.map {
                    resolve(it)
                }.filter {
                    it != null
                }.map {
                    it!!
                }
                fieldResolvers.forEach {
                    container.addView(it.bind(poiData?.get(it.name)))
                }
            }
        }
    }

    private var photoOutputFileUri: Uri? = null
    private val permissionHandlers = mutableMapOf<Int, () -> Unit>()

    private var targetImagesFieldResolver: ImagesFieldResolver? = null
    private var videoOutputFileUri: Uri? = null
    private var targetVideoFieldResolver: VideoFieldResolver? = null

    private fun resolve(field: POIType.Field) = when (field.type) {
        POIType.FieldType.STRING ->
            StringFieldResolver(field.name, this)
        POIType.FieldType.TEXT ->
            TextFieldResolver(field.name, this)
        POIType.FieldType.IMAGES ->
            ImagesFieldResolver(field.name, poi!!, {
                fieldResolver ->
                // see http://stackoverflow.com/questions/4455558/allow-user-to-select-camera-or-gallery-for-image
                permissionHandlers[REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_IMAGE] = {
                    permissionHandlers[REQUEST_CAMERA] = {
                        photoOutputFileUri = Uri.fromFile(File.createTempFile(
                                SimpleDateFormat("yyyyMMdd_HHmmss").format(Date()),
                                ".jpg",
                                File(poi!!.dir, fieldResolver.name).apply {
                                    if (!exists()) {
                                        mkdirs()
                                    }
                                }
                        ))
                        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            if (resolveActivity(packageManager) != null) {
                                putExtra(MediaStore.EXTRA_OUTPUT, photoOutputFileUri)
                                targetImagesFieldResolver = fieldResolver
                                startActivityForResult(this, SELECT_IMAGE)
                            }
                        }
                    }
                    assertPermission(Manifest.permission.CAMERA, REQUEST_CAMERA).successUi {
                        permissionHandlers[REQUEST_CAMERA]?.invoke()
                    }
                }
                assertPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_IMAGE) successUi {
                    permissionHandlers[REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_IMAGE]?.invoke()
                }
            }, {
                fieldResolver, images, pos ->
                targetImagesFieldResolver = fieldResolver
                startActivityForResult(Intent(this, CarouselActivity::class.java).apply {
                    putStringArrayListExtra(CarouselActivity.TAG_IMAGES, ArrayList(images))
                    putExtra(CarouselActivity.TAG_POS, pos)
                }, VIEW_CAROUSEL)


            })
        POIType.FieldType.VIDEO ->
            VideoFieldResolver(field.name, this, poi!!, {
                fieldResolver, path ->
                if (path == null) {
                    permissionHandlers[REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_VIDEO] = {
                        permissionHandlers[REQUEST_VIDEO_CAPTURE] = {
                            videoOutputFileUri = Uri.fromFile(File.createTempFile(
                                    SimpleDateFormat("yyyyMMdd_HHmmss").format(Date()),
                                    ".mp4",
                                    File(poi!!.dir, fieldResolver.name).apply {
                                        if (!exists()) {
                                            mkdirs()
                                        }
                                    }
                            ))
                            val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                            if (takeVideoIntent.resolveActivity(packageManager) != null) {
                                targetVideoFieldResolver = fieldResolver
                                takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoOutputFileUri)
                                startActivityForResult(takeVideoIntent, TAKE_VIDEO)
                            }
                        }
                        assertPermission(Manifest.permission.CAMERA, REQUEST_VIDEO_CAPTURE).successUi {
                            permissionHandlers[REQUEST_VIDEO_CAPTURE]?.invoke()
                        }
                    }
                    assertPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_VIDEO) successUi {
                        permissionHandlers[REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_VIDEO]?.invoke()
                    }
                } else {
                    object : AppCompatDialogFragment() {
                        override fun onCreateDialog(savedInstanceState: Bundle?) =
                                AlertDialog.Builder(context)
                                        .setView(layoutInflater.inflate(R.layout.view_video_dialog, null).apply {
                                            val videoView = findViewById(R.id.videoView) as VideoView
                                            val playButton = (findViewById(R.id.imageButton) as ImageButton).apply {
                                                setOnClickListener {
                                                    this.visibility = View.GONE
                                                    videoView.start()
                                                }
                                            }
                                            videoView.apply {
                                                setVideoURI(Uri.fromFile(File(poi!!.dir, path)))
                                                setMediaController(MediaController(context))
                                                setOnCompletionListener {
                                                    playButton.visibility = View.VISIBLE
                                                }
                                                playButton.visibility = View.GONE
                                                start()
                                            }

                                        })
                                        .setNeutralButton("删除", {
                                            dialog, which ->
                                            fieldResolver.path = null
                                        })
                                        .setNegativeButton(R.string.cancel, null)
                                        .create()
                    }.show(supportFragmentManager, "")
                }
            })
        else ->
            null
    }

    private fun collectData() = task {
        JSONObject().apply {
            fieldResolvers.forEach {
                it.populate(this, poi!!)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_edit_poi, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> if (isDirty) {
            ConfirmExitDialogFragment({
                this@EditPOIActivity.finish()
            }).show(supportFragmentManager, "")
            true
        } else {
            super.onOptionsItemSelected(item)
        }
        R.id.action_submit -> {
            if (isDirty) {
                permissionHandlers[REQUEST_WRITE_EXTERNAL_STORAGE] = {
                    collectData() then {
                        formData ->
                        poi?.saveData(formData)?.successUi {
                            toast(R.string.poi_data_saved)
                            poiData = mutableMapOf<String, Any?>().apply {
                                formData.keys().forEach {
                                    this@apply[it] = formData.get(it)
                                }
                            }
                            fieldResolvers.forEach {
                                when (it) {
                                    is ImagesFieldResolver -> {
                                        val haystack = setOf(*it.images.toTypedArray())
                                        File(poi!!.dir, it.name).let {
                                            if (it.exists()) {
                                                it.listFiles { file ->
                                                    file.name.matches(jpegRegex) and
                                                            !haystack.contains(file.relativeTo(poi!!.dir).path)
                                                }.forEach {
                                                    Logger.v("${it.path} will be removed")
                                                    it.delete()
                                                }
                                            }
                                        }
                                    }
                                    is VideoFieldResolver -> {
                                        val resolver = it
                                        File(poi!!.dir, it.name).let {
                                            if (it.exists()) {
                                                it.listFiles { file ->
                                                    file.name.matches(mpegRegex) and (file.relativeTo(poi!!.dir).path
                                                            != resolver.path)
                                                }.forEach {
                                                    Logger.v("${it.path} will be removed")
                                                    it.delete()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                assertPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        REQUEST_WRITE_EXTERNAL_STORAGE) then {
                    permissionHandlers[REQUEST_WRITE_EXTERNAL_STORAGE]?.invoke()
                }
            } else {
                toast(R.string.poi_not_changed)
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }


    private val isDirty: Boolean
        get() = poiType.fields.any {
            field ->
            fieldResolvers.find { it.name == field.name }!!.changed(poiData?.get(field.name)).apply {
                Logger.v("$this, ${field.name}, ${poiData?.get(field.name)}")
            }
        }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_WRITE_EXTERNAL_STORAGE,
            REQUEST_CAMERA,
            REQUEST_READ_EXTERNAL_STORAGE,
            REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_VIDEO,
            REQUEST_VIDEO_CAPTURE,
            REQUEST_WRITE_EXTERNAL_STORAGE_SAVE_IMAGE ->
                if (grantResults.isNotEmpty()
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionHandlers[requestCode]?.invoke()
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            SELECT_IMAGE -> {
                if (resultCode == RESULT_OK) {
                    //                val isCamera = (data == null ||
                    //                        data.action == android.provider.MediaStore.ACTION_IMAGE_CAPTURE ||
                    //                        data.action == "inline-data")
                    //                if (!isCamera) {
                    //                    contentResolver.openInputStream(data?.data).copyTo(File(outputFileUri!!.path))
                    //                }
                    targetImagesFieldResolver?.add(File(photoOutputFileUri!!.path).relativeTo(poi!!.dir.absoluteFile).path)
                } else if (resultCode == RESULT_CANCELED) {
                    File(photoOutputFileUri!!.path).delete()
                }
            }
            VIEW_CAROUSEL -> {
                if (resultCode == RESULT_OK) {
                    targetImagesFieldResolver?.reset(data?.getStringArrayListExtra(CarouselActivity.TAG_IMAGES)?.map {
                        File(it).relativeTo(poi!!.dir.absoluteFile).path
                    })
                }
            }
            TAKE_VIDEO ->
                if (resultCode == RESULT_OK) {
                    targetVideoFieldResolver?.path = File(videoOutputFileUri!!.path).relativeTo(poi!!.dir).path
                    Logger.v(targetVideoFieldResolver?.path)
                } else if (resultCode == RESULT_CANCELED) {
                    File(videoOutputFileUri!!.path).delete()
                }
        }
    }

    override fun onBackPressed() {
        if (isDirty) {
            ConfirmExitDialogFragment({
                super.onBackPressed()
            }).show(supportFragmentManager, "")
        } else {
            super.onBackPressed()
        }
    }
}

private class ConfirmExitDialogFragment(val after: () -> Unit) : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?) =
            AlertDialog.Builder(context).setMessage(R.string.confirm_poi_no_save)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.action_ok, {
                        dialog, which ->
                        after()
                    })
                    .create()
}
