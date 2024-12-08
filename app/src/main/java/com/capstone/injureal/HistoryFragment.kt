package com.capstone.injureal

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.capstone.injureal.HistoryAdapter
import com.capstone.injureal.Result.Companion.REQUEST_HISTORY_UPDATE
import com.capstone.injureal.riwayat.AppDatabase
import com.capstone.injureal.riwayat.PredictionHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class HistoryFragment : Fragment(), HistoryAdapter.OnDeleteClickListener {

    private lateinit var predictionRecyclerView: RecyclerView
    private lateinit var predictionAdapter: HistoryAdapter
    private var predictionList: MutableList<PredictionHistory> = mutableListOf()
    private lateinit var tvNotFound: TextView

    companion object {
        const val TAG = "historydata"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the fragment layout
        val binding = inflater.inflate(R.layout.fragment_history, container, false)

        predictionRecyclerView = binding.findViewById(R.id.rvHistory)
        tvNotFound = binding.findViewById(R.id.tvNotFound)

        // Setup RecyclerView and Adapter
        predictionAdapter = HistoryAdapter(predictionList)
        predictionAdapter.setOnDeleteClickListener(this)
        predictionRecyclerView.adapter = predictionAdapter
        predictionRecyclerView.layoutManager = LinearLayoutManager(activity)

        // Load prediction history from database
        GlobalScope.launch(Dispatchers.Main) {
            loadPredictionHistoryFromDatabase()
        }

        return binding
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_HISTORY_UPDATE && resultCode == RESULT_OK) {
            GlobalScope.launch(Dispatchers.Main) {
                loadPredictionHistoryFromDatabase()
            }
        }
    }

    private fun loadPredictionHistoryFromDatabase() {
        GlobalScope.launch(Dispatchers.Main) {
            val predictions = AppDatabase.getDatabase(requireContext()).predictionHistoryDao().getAllPredictions()
            predictionList.clear()
            predictionList.addAll(predictions)
            predictionAdapter.notifyDataSetChanged()
            showOrHideNoHistoryText()
        }
    }

    private fun showOrHideNoHistoryText() {
        if (predictionList.isEmpty()) {
            tvNotFound.visibility = View.VISIBLE
            predictionRecyclerView.visibility = View.GONE
        } else {
            tvNotFound.visibility = View.GONE
            predictionRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDeleteClick(position: Int) {
        val prediction = predictionList[position]
        if (prediction.result.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.IO) {
                AppDatabase.getDatabase(requireContext()).predictionHistoryDao().deletePrediction(prediction)
            }
            predictionList.removeAt(position)
            predictionAdapter.notifyDataSetChanged()
            showOrHideNoHistoryText()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.bottom_nav_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                activity?.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
