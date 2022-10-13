package androidapp

//import com.example.bottomsheeterrorreportingreproduction.androidapp.activities.sharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bottomsheeterrorreportingreproduction.R
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.AndroidFunctionLibrary.getSpeakerName
import com.example.bottomsheeterrorreportingreproduction.androidapp.util.Util
import com.google.android.material.card.MaterialCardView
import timber.log.Timber

class ShiurAdapter :
    ListAdapter<ShiurWithAllFilterMetadata, ShiurAdapter.ShiurViewHolder>(
        object : DiffUtil.ItemCallback<ShiurWithAllFilterMetadata>() {
            override fun areItemsTheSame(
                oldItem: ShiurWithAllFilterMetadata,
                newItem: ShiurWithAllFilterMetadata
            ): Boolean {
                return oldItem == newItem //checks ID anyway
            }

            override fun areContentsTheSame(
                oldItem: ShiurWithAllFilterMetadata,
                newItem: ShiurWithAllFilterMetadata
            ): Boolean {
                return oldItem.nonContentIsTheSame(newItem) && oldItem.contentIsTheSame(
                    newItem
                )
            }
        }
    ) {
    init {
        submitList(
            shiurim
        )
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ShiurViewHolder {
        return ShiurViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.individual_shiur_card_layout, parent, false)
        )
    }


    override fun onBindViewHolder(holder: ShiurViewHolder, position: Int) {
        val item = getItem(position)
        holder.shiur = item
        holder.bindItem(item)
    }

    open inner class ShiurViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        var shiur: ShiurWithAllFilterMetadata = ShiurWithAllFilterMetadata.blankShiur()
        val card = itemView.findViewById(R.id.card) as MaterialCardView
        private val shiurTitle = itemView.findViewById(R.id.shiur_title) as TextView?
        private val shiurSpeaker = itemView.findViewById(R.id.shiur_speaker) as TextView?

        init {
            card.setOnClickListener {
                Timber.v("$it clicked")
                Util.playShiur(shiur)
            }
        }

        fun bindItem(item: ShiurWithAllFilterMetadata) {
            shiurTitle?.text = Util.getTitle(item)
            shiurSpeaker?.text = getSpeakerName(item)
        }
    }
    companion object {
        val shiurim = listOf(
            ShiurWithAllFilterMetadata(
                1037662,
                "Ruth 17 Perek 4 Posuk 12",
                "Rabbi Pesach Siegel",
                "Sefer Rus",
                "",
                3198,
                13,
                72,
                "September 1, 2022",
                false,
                false,
                true,
                false,
                "null",
                null,
                null,
                null,
                null,
                "",
                null,
                -1,
                0,
            ), ShiurWithAllFilterMetadata(
                1037661,
                "Ruth 16 Perek 4 Posuk 08",
                "Rabbi Pesach Siegel",
                "Sefer Rus",
                "",
                2081,
                13,
                72,
                "September 1, 2022",
                false,
                false,
                true,
                false,
                "null",
                null,
                null,
                null,
                null,
                "",
                null,
                -1,
                0,
            ), ShiurWithAllFilterMetadata(
                1037660,
                "Ruth 15 Perek 4 Posuk 04",
                "Rabbi Pesach Siegel",
                "Sefer Rus",
                "",
                2336,
                13,
                72,
                "September 1, 2022",
                false,
                false,
                true,
                false,
                "null",
                null,
                null,
                null,
                null,
                "",
                null,
                -1,
                0,
            ), ShiurWithAllFilterMetadata(
                1037659,
                "Ruth 14 Perek 3 Posuk 16",
                "Rabbi Pesach Siegel",
                "Sefer Rus",
                "",
                2562,
                13,
                72,
                "September 1, 2022",
                false,
                false,
                true,
                false,
                "null",
                null,
                null,
                null,
                null,
                "",
                null,
                -1,
                0,
            ), ShiurWithAllFilterMetadata(
                1037658,
                "Ruth 13 Perek 3 Posuk 11",
                "Rabbi Pesach Siegel",
                "Sefer Rus",
                "",
                2765,
                13,
                72,
                "September 1, 2022",
                false,
                false,
                true,
                false,
                "null",
                null,
                null,
                null,
                null,
                "",
                null,
                -1,
                0,
            )
        )
    }
}

