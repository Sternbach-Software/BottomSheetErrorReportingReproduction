package androidapp

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bottomsheeterrorreportingreproduction.R
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.ld
import com.google.android.material.progressindicator.CircularProgressIndicator

class ShiurimFragment : Fragment(R.layout.activity_shiurim_page) {

    protected lateinit var recyclerView: RecyclerView
    protected lateinit var progressIndicator: CircularProgressIndicator
    protected lateinit var numShiur: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ld("onCreate() called, bundle = $savedInstanceState, arguments = $arguments")
        super.onCreate(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ld("onViewCreated() called, bundle = $savedInstanceState")
        activity?.title = "Shiurim"
        view.apply {
            recyclerView = findViewById(R.id.fast_scroller)
            progressIndicator =
                findViewById(R.id.shiur_progress_indicator) //This was renamed accidentally from metadata_population_progress_indicator, but should be removed anyway.
            numShiur = findViewById(R.id.num_shiur)
        }
        recyclerView.adapter = ShiurAdapter()
        recyclerView.layoutManager = LinearLayoutManager(context)
    }
}