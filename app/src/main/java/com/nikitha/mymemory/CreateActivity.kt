package com.nikitha.mymemory

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.nikitha.mymemory.models.BoardSize
import com.nikitha.mymemory.utils.*
import java.io.ByteArrayOutputStream

class CreateActivity : AppCompatActivity() {

    private lateinit var boardSize: BoardSize
    private var numImageRequired  = -1

    private lateinit var rvImagePicker: RecyclerView
    private lateinit var btnSave: Button
    private lateinit var etGameNAme : EditText
    private lateinit var pbUploading: ProgressBar

    private lateinit var adapter: ImagePickerAdapter

    private val chosenImageUris = mutableListOf<Uri>()

    private val storage = Firebase.storage
    private val db = Firebase.firestore


    companion object{
        private const val PICK_PHOTO_CODE = 246
        private const val READ_EXTERNAL_PHOTOS_CODE = 248
        private const val READ_PHOTOS_PERMISSION = android.Manifest.permission.READ_EXTERNAL_STORAGE
        private const val TAG = "CreateActivity"
        private const val MIN_GAME_NAME_LENGTH = 3
        private const val MAX_GAME_NAME_LENGTH = 14

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create)

        rvImagePicker = findViewById(R.id.rvImagePicker)
        btnSave = findViewById(R.id.btnSave)
        etGameNAme = findViewById(R.id.etGameName)
        pbUploading = findViewById(R.id.pbUploading)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        boardSize = intent.getSerializableExtra(EXTRA_BOARD_SIZE)as BoardSize
        numImageRequired = boardSize.getNumPairs()

        supportActionBar?.title = "Choose pics 0/$numImageRequired"

        btnSave.setOnClickListener {
            saveDatatoFirebase()

        }


        etGameNAme.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_NAME_LENGTH))
        etGameNAme.addTextChangedListener(object:TextWatcher{

            override fun afterTextChanged(p0: Editable?) {
                btnSave.isEnabled = shouldEnableSaveButton()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }


        })



        adapter = ImagePickerAdapter(this,chosenImageUris, boardSize , object: ImagePickerAdapter.ImageClickListener{
            override fun onPlaceHolderClicked() {
                if(IsPermissionGranted(this@CreateActivity, READ_PHOTOS_PERMISSION))
                {
                        launchIntentForPhotos()
                }
                else
                    requestForPermission(this@CreateActivity,READ_PHOTOS_PERMISSION, READ_EXTERNAL_PHOTOS_CODE)

            }

        })
        rvImagePicker.adapter = adapter
        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == READ_EXTERNAL_PHOTOS_CODE){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                launchIntentForPhotos()
            else
                Toast.makeText(this, "In order to create a custom game, permission is required.", Toast.LENGTH_LONG).show()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home)
        {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }


    private fun launchIntentForPhotos() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // allow user to pick multiple images
        startActivityForResult(Intent.createChooser(intent, "Choose pics here"), PICK_PHOTO_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if(requestCode != PICK_PHOTO_CODE || resultCode!= Activity.RESULT_OK || data == null){
            Log.w(TAG, "No data , user likely cancelled the flow")
            return
        }

        val selectedUri = data.data
        val clipdata = data.clipData
        if(clipdata!=null)
        {
            Log.i(TAG, "Clipdata images ${clipdata.itemCount} $clipdata")
            for(i in 0 until clipdata.itemCount)
            {
                val clipItem = clipdata.getItemAt(i)
                chosenImageUris.add(clipItem.uri)
            }
        }
        else if(selectedUri!=null)
        {
            chosenImageUris.add(selectedUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Chosen images: ${chosenImageUris.size}/$numImageRequired"

        btnSave.isEnabled = shouldEnableSaveButton()
    }

    private fun shouldEnableSaveButton(): Boolean {
        //Check if we should enable the save button
        if(chosenImageUris.size < numImageRequired)
            return false
        if(etGameNAme.text.isBlank() || etGameNAme.text.length < MIN_GAME_NAME_LENGTH)
            return false

        return true
    }


    private fun saveDatatoFirebase() {
        val customGameName = etGameNAme.text.toString()
        btnSave.isEnabled = false
        //Check if we are overriding some one else's data
        db.collection("games").document(customGameName).get().addOnSuccessListener { document->
                if(document!=null && document.data!=null){
                    AlertDialog.Builder(this)
                        .setTitle("Name taken")
                        .setMessage("A game with the same name already exists. Please choose another")
                        .setPositiveButton("OK", null)
                        .show()

                    btnSave.isEnabled = true
                }
            else
                handleImageUploading(customGameName)
        }.addOnFailureListener { exception->
            Log.e(TAG, "Encountred error while saving game", exception)
            Toast.makeText(this, "Encountred error while saving game", Toast.LENGTH_LONG).show()
            btnSave.isEnabled = true
        }

    }

    private fun handleImageUploading(customGameName: String) {
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()

        pbUploading.visibility = View.VISIBLE

        Log.i(TAG, "Images saving to firebase ")

        for((index, photoUri) in chosenImageUris.withIndex()) {
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "image/$customGameName/${System.currentTimeMillis()}-{$index}.jpg"
            val photoReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                .continueWithTask { photoUploadTask ->
                    Log.i(TAG, "Uploaded bytes: ${photoUploadTask.result?.bytesTransferred}")
                    photoReference.downloadUrl
                }.addOnCompleteListener { downloadUrlTask ->
                    if (!downloadUrlTask.isSuccessful) {
                        Log.e(TAG, "Exception with firebase storage", downloadUrlTask.exception)
                        Toast.makeText(this, "Files couldn't be uploaded", Toast.LENGTH_LONG).show()
                        didEncounterError = true
                        return@addOnCompleteListener
                    }
                    if (didEncounterError) {
                        pbUploading.visibility = View.GONE
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downloadUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    pbUploading.progress = uploadedImageUrls.size * 100 / chosenImageUris.size

                    Log.i(TAG, "Image uploaded: $photoUri, num uploaded ${uploadedImageUrls.size}")

                    if(uploadedImageUrls.size == chosenImageUris.size)
                        handleAllImagesUploaded(customGameName, uploadedImageUrls)
                }
        }

    }

    private fun handleAllImagesUploaded(gameName: String, imageUrls: MutableList<String>) {
            //upload this info to firestore
        //each memory game is a document and that livess in a collection
        db.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener{ gameCreationTask ->
                pbUploading.visibility = View.GONE
                if(!gameCreationTask.isSuccessful) {
                    Log.e(TAG, "Exception with game creation", gameCreationTask.exception)
                    Toast.makeText(this, "Failed game creation", Toast.LENGTH_LONG).show()
                    return@addOnCompleteListener
                }

                Log.i(TAG, "Successfully uploaded the game")
                AlertDialog.Builder(this)
                    .setTitle("Upload complete! Let's play your game $gameName")
                    .setPositiveButton("OK"){ _,_, ->
                        val resultData = Intent()
                        resultData.putExtra(EXTRA_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }.show()
            }




    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        }
        else
            MediaStore.Images.Media.getBitmap(contentResolver,photoUri)


        Log.i(TAG, "Original width : ${originalBitmap.width} and original height ${originalBitmap.height}")
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)

        Log.i(TAG, "Original width : ${scaledBitmap.width} and original height ${scaledBitmap.height}")

        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }
}
