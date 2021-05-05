package com.rodriguesporan.itransfer.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.databinding.DataBindingUtil
import coil.load
import coil.transform.CircleCropTransformation
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.rodriguesporan.itransfer.R
import com.rodriguesporan.itransfer.data.Transaction
import com.rodriguesporan.itransfer.data.User
import com.rodriguesporan.itransfer.databinding.ActivitySendPaymentBinding
import java.util.*

class SendPaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySendPaymentBinding
    private lateinit var receiverUid: String
    private lateinit var senderUid: String

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_send_payment)
        binding.sendPaymentActivity = this

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (intent.extras != null) {
            receiverUid = intent.extras!!.get("RECEIVER_UID").toString()
            senderUid = intent.extras!!.get("SENDER_UID").toString()
            updateUI()
        }
    }

    fun pay() {
        val transactionAmount: Double = binding.amountTextInputEditText.text.toString().toDoubleOrNull() ?: 0.0
        val newTransactionRef: DocumentReference = db.collection("transactions").document()
        val transaction = Transaction(
                newTransactionRef.id,
                transactionAmount,
                senderUid,
                receiverUid,
                Date(),
                arrayListOf(senderUid, receiverUid)
        )

        newTransactionRef.set(transaction)
                .addOnSuccessListener {
                    Log.d(TAG, "DocumentSnapshot successfully written!")

                    val senderDocRef =  db.collection("users").document(senderUid)
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(senderDocRef)
                        val newTotal = snapshot.getDouble("total")?.minus(transactionAmount)
                        transaction.update(senderDocRef, "total", newTotal)
                        transaction.update(senderDocRef, "lastWeekTransactionsUid", FieldValue.arrayUnion(newTransactionRef.id))

                        null
                    }.addOnSuccessListener { Log.d(TAG, "Transaction success!") }
                            .addOnFailureListener { e -> Log.w(TAG, "Transaction failure.", e) }

                    val receiverDocRef =  db.collection("users").document(receiverUid)
                    db.runTransaction { transaction ->
                        val snapshot = transaction.get(receiverDocRef)
                        transaction.update(receiverDocRef, "total", FieldValue.increment(transactionAmount))
                        transaction.update(receiverDocRef, "lastWeekTransactionsUid", FieldValue.arrayUnion(newTransactionRef.id))

                        null
                    }.addOnSuccessListener { Log.d(TAG, "Transaction success!") }
                            .addOnFailureListener { e -> Log.w(TAG, "Transaction failure.", e) }

                    startActivity(Intent(this, PaymentReceiptActivity::class.java))
                }
                .addOnFailureListener { e -> Log.w(TAG, "Error writing document", e) }
    }

    private fun updateUI() {
        if (receiverUid != null) {
            binding.receiverUidTextView.text = receiverUid

            db.collection("users")
                    .document(receiverUid).get()
                    .addOnSuccessListener { documentSnapshot ->
                        if (documentSnapshot.exists()) {
                            val user: User? = documentSnapshot.toObject(User::class.java)
                            binding.displayNameTextView.text = user?.displayName
                            binding.photoImageView.load(user?.photoUrl) {
                                placeholder(R.drawable.ic_baseline_person_24)
                                crossfade(true)
                                transformations(CircleCropTransformation())
                            }
                        }
                    }.addOnFailureListener { exception ->
                        Log.w(TAG, "Error getting user: ", exception)
                    }
        }
    }

    companion object {
        private const val TAG = "ITransfer"
    }
}