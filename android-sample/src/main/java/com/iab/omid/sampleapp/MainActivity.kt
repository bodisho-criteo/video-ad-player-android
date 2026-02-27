package com.iab.omid.sampleapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set up the toolbar as the action bar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Only add fragment if this is a fresh start (not a configuration change)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, ExampleSelectorFragment())
                .commit()
        }

        // Handle back navigation for action bar
        supportFragmentManager.addOnBackStackChangedListener {
            val canGoBack = supportFragmentManager.backStackEntryCount > 0
            supportActionBar?.setDisplayHomeAsUpEnabled(canGoBack)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        supportFragmentManager.popBackStack()
        return true
    }

    // Navigation helpers
    fun showBasicVideoPlayerScreen(vastUrl: String) {
        val fragment = BasicVideoPlayerFragment().apply {
            arguments = Bundle().apply {
                putString("vast_url", vastUrl)
            }
        }
        navigateToFragment(fragment)
    }

    fun showFeedVideoPlayerScreen(vastUrl: String) {
        val fragment = FeedVideoPlayerFragment().apply {
            arguments = Bundle().apply {
                putString("vast_url", vastUrl)
            }
        }
        navigateToFragment(fragment)
    }

    private fun navigateToFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.mainFragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        const val VAST_DEMO_URL_SINGLE_MEDIA = "https://raw.githubusercontent.com/criteo/vast-file-examples/refs/heads/main/server/sample_vast_app.xml"
        const val VAST_DEMO_URL_MULTIPLE_MEDIA = "https://raw.githubusercontent.com/criteo/vast-file-examples/refs/heads/main/server/sample_vast_app_multiple_media.xml"
        const val VAST_DEMO_URL_NO_CAPTIONS = "https://raw.githubusercontent.com/criteo/vast-file-examples/refs/heads/main/server/sample_vast_app_no_closed_caption.xml"
        const val VAST_DEMO_URL_CLICK_THROUGH = "https://raw.githubusercontent.com/criteo/vast-file-examples/refs/heads/main/server/sample_vast_app_with_clickthrough.xml"

    }
}
