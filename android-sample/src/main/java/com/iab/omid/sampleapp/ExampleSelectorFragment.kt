package com.iab.omid.sampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.iab.omid.sampleapp.MainActivity.Companion.VAST_DEMO_URL_CLICK_THROUGH
import com.iab.omid.sampleapp.MainActivity.Companion.VAST_DEMO_URL_MULTIPLE_MEDIA
import com.iab.omid.sampleapp.MainActivity.Companion.VAST_DEMO_URL_NO_CAPTIONS
import com.iab.omid.sampleapp.MainActivity.Companion.VAST_DEMO_URL_SINGLE_MEDIA

/**
 * A fragment that displays a list of example items for video ad demonstrations.
 *
 * Note: This class is scaffolding code for the demo app UI and is not relevant to the
 * video player integration. For the actual video player integration, see the fragment classes such
 * as [BasicVideoPlayerFragment].
 */
class ExampleSelectorFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_example_selector, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Video Ad Examples"

        view.findViewById<View>(R.id.basicVideoPlayerItem)?.setOnClickListener {
            val url = getSelectedVastUrl(view)
            (activity as? MainActivity)?.showBasicVideoPlayerScreen(url)
        }

        view.findViewById<View>(R.id.feedVideoPlayerItem)?.setOnClickListener {
            val url = getSelectedVastUrl(view)
            (activity as? MainActivity)?.showFeedVideoPlayerScreen(url)
        }
    }

    private fun getSelectedVastUrl(view: View): String {
        val radioGroup = view.findViewById<RadioGroup>(R.id.vastUrlRadioGroup)
        return when (radioGroup.checkedRadioButtonId) {
            R.id.vastUrlSingleMedia -> VAST_DEMO_URL_SINGLE_MEDIA
            R.id.vastUrlMultipleMedia -> VAST_DEMO_URL_MULTIPLE_MEDIA
            R.id.vastUrlNoClosedCaptions -> VAST_DEMO_URL_NO_CAPTIONS
            R.id.vastUrlWithClickThrough -> VAST_DEMO_URL_CLICK_THROUGH
            else -> VAST_DEMO_URL_SINGLE_MEDIA
        }
    }
}
