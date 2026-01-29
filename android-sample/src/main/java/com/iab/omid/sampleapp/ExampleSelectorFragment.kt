package com.iab.omid.sampleapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

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
            (activity as? MainActivity)?.showBasicVideoPlayerScreen()
        }

        view.findViewById<View>(R.id.feedVideoPlayerItem)?.setOnClickListener {
            (activity as? MainActivity)?.showFeedVideoPlayerScreen()
        }
    }
}
