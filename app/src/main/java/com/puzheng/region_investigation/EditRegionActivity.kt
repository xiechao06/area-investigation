package com.puzheng.region_investigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.PersistableBundle
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.Toast
import com.amap.api.location.AMapLocation
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.orhanobut.logger.Logger
import com.puzheng.region_investigation.model.POI
import com.puzheng.region_investigation.model.POIType
import com.puzheng.region_investigation.model.Region
import com.puzheng.region_investigation.store.POIStore
import com.puzheng.region_investigation.store.POITypeStore
import com.puzheng.region_investigation.store.RegionStore
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_edit_region.*
import kotlinx.android.synthetic.main.app_bar_edit_region_name.*
import kotlinx.android.synthetic.main.content_edit_region.*
import kotlinx.android.synthetic.main.fragment_edit_region.*
import kotlinx.android.synthetic.main.poi_bottom_sheet.*
import nl.komponents.kovenant.ui.successUi
import java.util.*

private val REQUEST_WRITE_EXTERNAL_STORAGE = 100
private val REQUEST_ACCESS_FINE_LOCATION: Int = 101

class EditRegionActivity : AppCompatActivity(), EditRegionActivityFragment.OnFragmentInteractionListener,
        POIFilterDialogFragment.OnFragmentInteractionListener {

    val fragmentEditRegion: EditRegionActivityFragment by lazy {
        findFragmentById<EditRegionActivityFragment>(R.id.fragment_edit_region)!!
    }

    override fun onFilterPOI(hiddenPOITypes: Set<POIType>) {
        fragmentEditRegion.hiddenPOITypes = hiddenPOITypes
        invalidateOptionsMenu()
    }

    private val bottomSheetBehavior: BottomSheetBehavior<out View> by lazy {
        BottomSheetBehavior.from(design_bottom_sheet)
    }

    private var poiRelocateActionMode: ActionMode? = null

    override fun onPOIMarkerSelected(marker: Marker?) {
        if (marker == null) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            fab?.visibility = View.VISIBLE
        } else {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            fab?.visibility = View.GONE
        }
    }

    private var selectedVertex: LatLng? = null
    private var dataChanged = false

    override fun onOutlineMarkerSelected(position: LatLng?) {
        selectedVertex = position
        editOutlineActionMode?.menu?.findItem(R.id.action_delete)?.isVisible = selectedVertex != null
    }

    private var editOutlineActionMode: ActionMode? = null

    override fun onMapLongClick() {
        if (editOutlineActionMode != null) {
            return
        }

        editOutlineActionMode = startSupportActionMode(object : ActionMode.Callback {
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                mode?.setTitle(R.string.editing_outline)
                return false
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = when (item?.itemId) {
                R.id.action_delete -> {
                    if (selectedVertex != null) {
                        fragmentEditRegion.deleteVertex(selectedVertex!!)
                    }
                    true
                }
                R.id.action_submit -> {
                    fragmentEditRegion.saveOutline({
                        // 注意， 一定要告诉Picasso清除图片缓存
                        Picasso.with(this@EditRegionActivity).invalidate(RegionStore.with(this@EditRegionActivity).getCoverImageFile(region))
                    })
                    dataChanged = true
                    true
                }
                else -> false

            }

            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                mode?.menuInflater?.inflate(R.menu.context_menu_edit_region_outline, menu)
                fragmentEditRegion.editMode = EditRegionActivityFragment.Companion.EditMode.EDIT_OUTLINE
                fab?.visibility = View.GONE
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
                editOutlineActionMode = null
                fragmentEditRegion.restoreOutline()
                fragmentEditRegion.editMode = EditRegionActivityFragment.Companion.EditMode.DEFAULT
                fab?.visibility = View.VISIBLE
            }
        })

    }

    override lateinit var region: Region

    private val permissionRequestHandlerMap: MutableMap<String, () -> Unit> = mutableMapOf()

    private fun fetchPOITypes(after: (List<POIType>) -> Unit) {
        POITypeStore.with(this).list successUi {
            if (it != null && it.isNotEmpty()) {
                after(it)
            } else {
                val store = POITypeStore.with(this@EditRegionActivity)
                this@EditRegionActivity.toast(resources.getString(R.string.no_poi_type_meta_info, store.dir.absolutePath),
                        Toast.LENGTH_LONG)
                permissionRequestHandlerMap[Manifest.permission.WRITE_EXTERNAL_STORAGE] = {
                    mkPOITypeDir()
                }
                assertPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        REQUEST_WRITE_EXTERNAL_STORAGE).successUi {
                    permissionRequestHandlerMap[Manifest.permission.WRITE_EXTERNAL_STORAGE]?.invoke()
                }
            }
        }
        // TODO it should be polite to show a progressbar

    }

    private fun mkPOITypeDir() {
        POITypeStore.with(this).dir.apply {
            Logger.v("poi type dir is $absolutePath")
            if (mkdirs()) {
                Logger.e("can't make directory $absolutePath")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        region = if (savedInstanceState != null) {
            savedInstanceState.getParcelable(RegionListActivity.TAG_REGION)!!
        } else {
            intent.getParcelableExtra<Region>(RegionListActivity.TAG_REGION)
        }
        setContentView(R.layout.activity_edit_region)
        Logger.init("EditRegionActivity")
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        fab.setOnClickListener {
            fetchPOITypes {
                POITypeChooseDialog(it, { addPOI(it) })
                        .show(supportFragmentManager, "")
            }

        }
        updateContent()
        design_bottom_sheet.findView<ImageButton>(R.id.trash).setOnClickListener {
            fragmentEditRegion.removeSelectedPOIMarker()
            onPOIMarkerSelected(null)
        }
        design_bottom_sheet.findView<ImageButton>(R.id.edit).setOnClickListener {
            startActivity(Intent(this, EditPOIActivity::class.java).apply {
                putExtra(EditPOIActivity.TAG_POI, fragmentEditRegion.selectedPOI)
            })
        }
        design_bottom_sheet.findView<ImageButton>(R.id.relocate).setOnClickListener {

            poiRelocateActionMode = startSupportActionMode(object : ActionMode.Callback {
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    mode?.setTitle(R.string.poi_relocating)
                    return false
                }

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = when (item?.itemId) {
                    R.id.action_submit -> {
                        fragmentEditRegion.savePOILocation()
                        true
                    }
                    else -> false
                }

                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    mode?.menuInflater?.inflate(R.menu.context_menu_edit_region_outline, menu)
                    fragmentEditRegion.editMode = EditRegionActivityFragment.Companion.EditMode.POI_RELOCATE
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    fab?.visibility = View.GONE
                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                    fragmentEditRegion.restorePOILocation()
                    poiRelocateActionMode = null
                    fragmentEditRegion.editMode = EditRegionActivityFragment.Companion.EditMode.DEFAULT
                    fab?.visibility = View.VISIBLE
                }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        Logger.v("save instance state")
        outState?.putParcelable(RegionListActivity.TAG_REGION, region)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_edit_region, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.action_filter)?.icon = ContextCompat.getDrawable(
                this,
                if (fragmentEditRegion.hiddenPOITypes.isNotEmpty()) {
                    R.drawable.vector_drawable_filter_activated
                } else {
                    R.drawable.vector_drawable_filter
                }
        )
        return super.onPrepareOptionsMenu(menu)
    }

    private var editNameActionMode: ActionMode? = null

    override fun onOptionsItemSelected(item: MenuItem?): Boolean = when (item?.itemId) {
        R.id.action_edit_name -> {
            editNameActionMode = startSupportActionMode(object : ActionMode.Callback {
                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    mode?.customView = layoutInflater.inflate(R.layout.app_bar_edit_region_name, null, false)
                    region_name.apply {
                        setText(region.name)
                        requestFocus()
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(currentFocus, 0)
                        setSelection(region.name.length)
                    }
                    return true
                }

                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                    when (item?.itemId) {
                        R.id.action_ok -> {
                            editNameActionMode!!.finish()
                            region.name = region_name.text.toString()
                            updateContent()
                            RegionStore.with(this@EditRegionActivity).updateName(region.id!!, region_name.text.toString()) successUi {
                                toast(R.string.edit_region_name_success)
                                dataChanged = true
                            }
                        }
                    }
                    return true
                }


                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    mode?.menuInflater?.inflate(R.menu.context_menu_edit_region_name, menu)
                    return true
                }

                override fun onDestroyActionMode(mode: ActionMode?) {
                }
            })
            true
        }
        R.id.action_show_stat -> {
            RegionStatDialogFragment(region).show(supportFragmentManager, "")
            true
        }
        R.id.action_filter -> {
            POIFilterDialogFragment(region,
                    fragmentEditRegion.hiddenPOITypes).show(supportFragmentManager, "")
            true
        }
        else ->
            super.onOptionsItemSelected(item)
    }

    private fun updateContent() {
        supportActionBar?.title = region.name
    }


    fun addPOI(poiType: POIType) {
        permissionRequestHandlerMap[Manifest.permission.ACCESS_FINE_LOCATION] = {
            getLocation(AMapLocation(Location("").apply {
                latitude = center.latitude
                longitude = center.longitude
            })).successUi {
                val poi = POI(
                        null,
                        poiType.uuid,
                        region.id!!,
                        LatLng(it.latitude, it.longitude),
                        Date())
                POIStore.with(this).create(poi) successUi {
                    toast(R.string.poi_created)
                    val marker = fragmentEditRegion.addPOI(poi.copy(id = it))
                    if (!marker.isVisible) {
                        Snackbar.make(findViewById(android.R.id.content)!!, R.string.create_hidden_poi, Snackbar.LENGTH_INDEFINITE).apply {
                            setAction(R.string.i_see, {
                                dismiss()
                            }).show()
                        }
                    }
                }
            }
        }
        assertPermission(Manifest.permission.ACCESS_FINE_LOCATION, REQUEST_ACCESS_FINE_LOCATION).successUi {
            permissionRequestHandlerMap[Manifest.permission.ACCESS_FINE_LOCATION]?.invoke()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_WRITE_EXTERNAL_STORAGE ->
                if (grantResults.isNotEmpty()
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionRequestHandlerMap[Manifest.permission.WRITE_EXTERNAL_STORAGE]?.invoke()
                } else {
                    toast("why not fake some poi types?")
                }
            REQUEST_ACCESS_FINE_LOCATION ->
                if (grantResults.isNotEmpty()
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionRequestHandlerMap[Manifest.permission.ACCESS_FINE_LOCATION]?.invoke()
                }
            EditRegionActivityFragment.REQUEST_ACCESS_FINE_LOCATION ->
                if (grantResults.isNotEmpty()
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    (fragment_edit_region as EditRegionActivityFragment).onPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION,
                            EditRegionActivityFragment.REQUEST_ACCESS_FINE_LOCATION)
                }
        }
    }

    val center: LatLng
        get() = fragmentEditRegion.map.map.cameraPosition.target
}




