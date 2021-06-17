package com.test.authentication

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.CountDownTimer
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.test.authentication.databinding.ActivityMainBinding
import com.test.authentication.databinding.ItemRankingBinding
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mUser: User
    private lateinit var mUserId: String
    private val mTopRanking = ArrayList<User>()
    private val mFirebaseDatabase = FirebaseDatabase.getInstance("https://my-authentication-7ff04-default-rtdb.asia-southeast1.firebasedatabase.app").reference.child("users")
    private val mFirebaseAuth = FirebaseAuth.getInstance()
    private val mRandom = Random()
    private var mQuestionAnswer = 0
    private val mGoogleSignInClient by lazy {
        GoogleSignIn.getClient(
            this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )
    }
    private val mCountDownTimer = object : CountDownTimer(6000, 100) {
        override fun onFinish() {
            checkAnswer(-1)
        }

        override fun onTick(millisUntilFinished: Long) {
            mBinding.progressBar.progress = (millisUntilFinished / 100).toInt()
        }
    }
    private val mRankingListener = object : ValueEventListener {
        override fun onCancelled(error: DatabaseError) {
        }

        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.hasChildren()) {
                mTopRanking.clear()
                mTopRanking.addAll(snapshot.children.map { it.getValue(User::class.java)!! }.reversed())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityMainBinding.inflate(layoutInflater).also { mBinding = it }.root)
        mFirebaseDatabase.orderByChild("score").limitToFirst(10).addValueEventListener(mRankingListener)

        with(mBinding) {
            btnSignIn.setOnClickListener {
                mSignInForResult.launch(mGoogleSignInClient.signInIntent)
            }
            btnStart.setOnClickListener {
                if (layoutAnswer.visibility == View.VISIBLE) {
                    mCountDownTimer.cancel()
                    layoutAnswer.visibility = View.GONE
                    tvQuestion.visibility = View.GONE
                    progressBar.visibility = View.GONE
                    btnStart.text = getString(R.string.start)
                } else {
                    mCountDownTimer.start()
                    layoutAnswer.visibility = View.VISIBLE
                    tvQuestion.visibility = View.VISIBLE
                    progressBar.visibility = View.VISIBLE
                    btnStart.text = getString(R.string.cancel)
                    generateQuestion()
                }
            }
            tvA.setOnClickListener {
                checkAnswer(0)
            }
            tvB.setOnClickListener {
                checkAnswer(1)
            }
            tvC.setOnClickListener {
                checkAnswer(2)
            }
            btnRanking.setOnClickListener {
                showRanking()
            }
            btnLogout.setOnClickListener {
                mGoogleSignInClient.signOut()
                mFirebaseAuth.signOut()
                recreate()
            }
        }

        mFirebaseAuth.currentUser?.let { user ->
            getInformation(user)
        }
    }

    private fun checkAnswer(answer: Int) {
        mUser.totalPlay += 1
        if (answer == mQuestionAnswer) {
            mUser.totalTrue += 1
        }
        if (mUser.totalPlay > 10) {
            mUser.score = (mUser.totalTrue * 10000F / mUser.totalPlay).roundToInt() / 100F
        }
        mFirebaseDatabase.child(mUserId).setValue(mUser)
        mBinding.tvScore.text = mUser.score.toString()
        mCountDownTimer.cancel()
        mCountDownTimer.start()
        generateQuestion()
    }

    private fun generateQuestion() {
        val a = mRandom.nextInt(50) + 50
        val b = mRandom.nextInt(13)
        mBinding.tvQuestion.text = getString(R.string.question, a, b)
        mQuestionAnswer = mRandom.nextInt(3)
        when (mQuestionAnswer) {
            0 -> {
                mBinding.tvA.text = (a * b).toString()
                mBinding.tvB.text = differences10(a * b).toString()
                mBinding.tvC.text = differences100(a * b).toString()
            }
            1 -> {
                mBinding.tvA.text = differences10(a * b).toString()
                mBinding.tvB.text = (a * b).toString()
                mBinding.tvC.text = differences100(a * b).toString()
            }
            2 -> {
                mBinding.tvA.text = differences10(a * b).toString()
                mBinding.tvB.text = differences100(a * b).toString()
                mBinding.tvC.text = (a * b).toString()
            }
        }
    }

    private fun differences10(input: Int): Int {
        return if (mRandom.nextInt(2) == 0) {
            input + (mRandom.nextInt(3) + 1) * 10
        } else {
            input - (mRandom.nextInt(3) + 1) * 10
        }
    }

    private fun differences100(input: Int): Int {
        return if (mRandom.nextInt(2) == 0) {
            input + (mRandom.nextInt(2) + 1) * 100
        } else {
            input - (mRandom.nextInt(2) + 1) * 100
        }
    }

    private val mSignInForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val data = GoogleSignIn.getSignedInAccountFromIntent(it.data)
        try {
            val account = data.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken!!, null)
            mFirebaseAuth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    mFirebaseAuth.currentUser?.let { user ->
                        getInformation(user)
                    }
                }
            }
        } catch (e: ApiException) {
        }
    }

    private fun getInformation(googleUser: FirebaseUser) {
        mUserId = googleUser.uid
        mFirebaseDatabase.child(mUserId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_SHORT).show()
            }

            override fun onDataChange(snapshot: DataSnapshot) {
                var user = snapshot.getValue(User::class.java)
                if (user == null) {
                    user = User().apply {
                        name = googleUser.displayName
                        email = googleUser.email
                        phone = googleUser.phoneNumber
                        image = googleUser.photoUrl?.toString()
                    }
                }
                mFirebaseDatabase.child(mUserId).setValue(user.apply { loginLastTime = Calendar.getInstance().timeInMillis })
                mUser = user
                showInformation()
            }
        })
    }

    private fun showInformation() {
        mBinding.tvName.text = mUser.name
        mBinding.tvEmail.text = mUser.email
        mBinding.tvPhone.text = mUser.phone
        mBinding.tvScore.text = mUser.score.toString()
        with(ConstraintSet()) {
            clone(mBinding.layoutContainer)
            clear(R.id.btnSignIn, ConstraintSet.START)
            connect(R.id.btnSignIn, ConstraintSet.END, R.id.layoutContainer, ConstraintSet.START)
            connect(R.id.btnStart, ConstraintSet.END, R.id.layoutContainer, ConstraintSet.END)
            connect(R.id.btnStart, ConstraintSet.START, R.id.layoutContainer, ConstraintSet.START)
            clear(R.id.layoutProfile, ConstraintSet.BOTTOM)
            connect(R.id.layoutProfile, ConstraintSet.TOP, R.id.layoutContainer, ConstraintSet.TOP)
            applyTo(mBinding.layoutContainer)
            TransitionManager.beginDelayedTransition(mBinding.layoutContainer, ChangeBounds().setInterpolator(OvershootInterpolator()))
        }
        if (mUser.image != null) {
            Thread {
                val bmp = BitmapFactory.decodeStream(URL(mUser.image).openConnection().getInputStream())
                runOnUiThread {
                    mBinding.imvAvatar.setImageBitmap(bmp)
                }
            }.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mCountDownTimer.cancel()
        mFirebaseDatabase.removeEventListener(mRankingListener)
    }

    private fun showRanking() {
        with(Dialog(this)) {
            val listView = ListView(this@MainActivity)
            setContentView(listView)
            window?.attributes?.width = WindowManager.LayoutParams.MATCH_PARENT
            window?.attributes?.height = WindowManager.LayoutParams.MATCH_PARENT
            show()
            listView.adapter = RankingAdapter(this@MainActivity, mTopRanking)
        }
    }

    private class RankingAdapter(private val context: Context, private val mUsers: List<User>) : BaseAdapter() {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val binding = if (convertView != null) {
                (convertView.tag as ViewHolder).binding
            } else {
                ItemRankingBinding.inflate(LayoutInflater.from(context), parent, false).apply {
                    root.tag = ViewHolder(this)
                }
            }
            binding.tvNumber.text = (position + 1).toString()
            binding.tvName.text = mUsers[position].name
            binding.tvScore.text = mUsers[position].score.toString()
            return binding.root
        }

        override fun getItem(position: Int): Any {
            return 0
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getCount(): Int {
            return mUsers.size
        }

        private class ViewHolder(val binding: ItemRankingBinding)
    }
}