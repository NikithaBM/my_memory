package com.nikitha.mymemory

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.jinatonic.confetti.CommonConfetti
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.nikitha.mymemory.models.BoardSize
import com.nikitha.mymemory.models.MemoryGame
import com.nikitha.mymemory.models.UserImageLists
import com.nikitha.mymemory.utils.EXTRA_BOARD_SIZE
import com.nikitha.mymemory.utils.EXTRA_GAME_NAME
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    companion object
    {
        private const val TAG = "MainActivity"
        private const val CREATE_REQUEST_CODE = 248
    }

    private lateinit var clRoot:CoordinatorLayout
    private lateinit var rvBoard:RecyclerView
    private lateinit var tvNumMoves:TextView
    private lateinit var tvNumPairs:TextView

    private lateinit var memoryGame: MemoryGame
    private lateinit var adapter:MemoryBoardAdapter

    private var boardSize:BoardSize = BoardSize.EASY

    private val db = Firebase.firestore
    private var gameName:String?=null
    private var customGameImages :List<String>? = null

    private var mediaPlayer : MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)
        setUpBoard()

    }



    override fun onCreateOptionsMenu(menu: Menu?):Boolean{
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId)
        {
            R.id.mi_refresh->{
                //set up the game again

                if(memoryGame.getNumMoves() > 0 && !memoryGame.haveWonGame())
                {
                    showAlertDialog(getString(R.string.quit_game), null, View.OnClickListener {
                        setUpBoard()
                    })
                }
                else
                    setUpBoard()
                return true
            }
            R.id.mi_newsize->{
                showNewSizeDialog()
                return true
            }

            R.id.mi_custom->{
                showCreationDialog()
                return true
            }

            R.id.miDownloadGame->{
                showDownloadDialog()
                return true
            }

            R.id.mi_playRandomCustGame ->{
                playRandomCustGame()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun playRandomCustGame() {
        val list: MutableList<String> = ArrayList()
        db.collection("games").get().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                for (document in task.result!!) {
                    list.add(document.id)
                }
                Log.d(TAG, list.toString())
                val randomGameNumber = (0..(list.size - 1)).random()
                downloadGame(list[randomGameNumber])
            }
            else {
                Log.d(TAG, "Error getting random custom game: $list[randomGameNumber] ", task.exception)
            }
        }
    }


    private fun showCreationDialog() {
        val boardSizeView = LayoutInflater.from(this ).inflate(R.layout.dialog_board_size, null)
        val radioGroupsize = boardSizeView.findViewById<RadioGroup>(R.id.rbGroup)

        showAlertDialog(getString(R.string.create_custom_game), boardSizeView, View.OnClickListener {
            val desiredBoardSize = when(radioGroupsize.checkedRadioButtonId){
                R.id.rbeasy -> BoardSize.EASY
                R.id.rbmedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
           //navigate to a new activity
            val intent = Intent(this, CreateActivity::class.java)
            intent.putExtra(EXTRA_BOARD_SIZE, desiredBoardSize)
            startActivityForResult(intent, CREATE_REQUEST_CODE )

        })

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if(requestCode == CREATE_REQUEST_CODE && resultCode == Activity.RESULT_OK){

            val customGameName = data?.getStringExtra(EXTRA_GAME_NAME)
            if(customGameName == null)
            {
                Log.e(TAG, "Got null custom name game from create activity")
                return
            }
            downloadGame(customGameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun downloadGame(customGameName: String) {
        db.collection("games").document(customGameName).get().addOnSuccessListener { document->
          val userImageList = document.toObject(UserImageLists::class.java)
            if(userImageList?.images == null){
                Log.e(TAG,"Invalid custom game from firestore")
                Snackbar.make(clRoot, getString(R.string.no_game_found) + "$customGameName", Snackbar.LENGTH_LONG).show()
                return@addOnSuccessListener
            }
            val numCards = userImageList.images.size * 2
            boardSize = BoardSize.getByValue(numCards)

            customGameImages = userImageList.images

            for(imageUrl in userImageList.images){
                //Though card isn't opened , fetch and keeps in cache
                Picasso.get().load(imageUrl).fetch()
            }
            Snackbar.make(clRoot, getString(R.string.cust_game_play)+ ": $customGameName",Snackbar.LENGTH_LONG).show()
            gameName = customGameName
            setUpBoard()
        }.addOnFailureListener{exception->
            Log.e(TAG, "Exception while retreiving image", exception)
        }

    }

    private fun showNewSizeDialog() {
        val boardSizeView = LayoutInflater.from(this ).inflate(R.layout.dialog_board_size, null)
        val radioGroupsize = boardSizeView.findViewById<RadioGroup>(R.id.rbGroup)
        when(boardSize){
            BoardSize.EASY ->   radioGroupsize.check(R.id.rbeasy)
            BoardSize.MEDIUM ->  radioGroupsize.check(R.id.rbmedium)
            BoardSize.HARD -> radioGroupsize.check(R.id.rbhard)
        }
        showAlertDialog(getString(R.string.choose_new_size), boardSizeView, View.OnClickListener {
        boardSize = when(radioGroupsize.checkedRadioButtonId){
            R.id.rbeasy -> BoardSize.EASY
            R.id.rbmedium -> BoardSize.MEDIUM
            else -> BoardSize.HARD
        }
            gameName = null
            customGameImages = null
            setUpBoard()
        })

    }

    private fun showAlertDialog(title: String, view:View?, positiveClickListener:View.OnClickListener) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _, ->
                positiveClickListener.onClick(null)
            }.show()
    }

    private fun updateGameWithFlip(position: Int) {
        //Error checking
        if(memoryGame.haveWonGame()){
            //alert the user if already won
            Snackbar.make(clRoot, getString(R.string.won_already_msg), Snackbar.LENGTH_LONG).show()
            return
        }

        if(memoryGame.isCardUp(position))
        {
            //alert the user for an invalid move
            Snackbar.make(clRoot, getString(R.string.invalid_move_msg), Snackbar.LENGTH_LONG).show()
            return
        }


        if(memoryGame.flipCard(position))
        {
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat()/boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.color_progress_none),
                ContextCompat.getColor(this, R.color.color_progress_full)
            )as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = getString(R.string.Pairs) + ":" + "${memoryGame.numPairsFound}/ ${boardSize.getNumPairs()}"
            //tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound}/ ${boardSize.getNumPairs()}"
            if(memoryGame.haveWonGame())
            {
                Snackbar.make(clRoot, getString(R.string.win_msg), Snackbar.LENGTH_LONG).show()
                CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.RED, Color.BLUE)).oneShot()

                mediaPlayer?.start()
               /* mediaPlayer?.setOnCompletionListener {
                   it?.reset()
                    it?.release()
                    mediaPlayer = null
                }*/


            }
        }


        /*
        // Actually flip the card
    if (memoryGame.flipCard(position)) {
      Log.i(TAG, "Found a match! Num pairs found: ${memoryGame.numPairsFound}")
      val color = ArgbEvaluator().evaluate(
        memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
        ContextCompat.getColor(this, R.color.color_progress_none),
        ContextCompat.getColor(this, R.color.color_progress_full)
      ) as Int
      tvNumPairs.setTextColor(color)
      tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"
      if (memoryGame.haveWonGame()) {
        Snackbar.make(clRoot, "You won! Congratulations.", Snackbar.LENGTH_LONG).show()
        CommonConfetti.rainingConfetti(clRoot, intArrayOf(Color.YELLOW, Color.GREEN, Color.MAGENTA)).oneShot()
      }
    }
         */



        tvNumMoves.text = getString(R.string.Moves) + ": ${memoryGame.getNumMoves()}"
        //tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()

    }
    private fun setUpBoard() {
        supportActionBar?.title = gameName ?: getString(R.string.app_name)

        when(boardSize){
            BoardSize.EASY ->
            {
                tvNumPairs.text = getString(R.string.numPairsEasy)
                tvNumMoves.text = getString(R.string.numMovesEasy)
            }
            BoardSize.MEDIUM -> {
                tvNumPairs.text = getString(R.string.numPairsMedium)
                tvNumMoves.text = getString(R.string.numMovesMedium)
            }
            BoardSize.HARD -> {

                tvNumPairs.text = getString(R.string.numPairsHard)
                tvNumMoves.text = getString(R.string.numMoveshard)
            }
        }


        tvNumPairs.setTextColor(ContextCompat.getColor(this, R.color.color_progress_none))
        memoryGame = MemoryGame(boardSize, customGameImages)

        adapter = MemoryBoardAdapter(this, boardSize, memoryGame.cards, object :MemoryBoardAdapter.CardClickListener{
            override fun onCardClick(position: Int) {
                Log.i(TAG, "Card clicked $position")
                updateGameWithFlip(position)
            }

        })
        mediaPlayer = MediaPlayer.create(this, R.raw.applause)
        rvBoard.adapter = adapter
        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
    }

    private fun showDownloadDialog() {
        val boardDownloadView = LayoutInflater.from(this).inflate( R.layout.dialog_download_board, null)
        showAlertDialog(getString(R.string.download_custom_game), boardDownloadView, View.OnClickListener {
            //Grab the text of the game name that user wants to download
            val etDownloadGame = boardDownloadView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        })
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop")
        mediaPlayer?.reset()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}