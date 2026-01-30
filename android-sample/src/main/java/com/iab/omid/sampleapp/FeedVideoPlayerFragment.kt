package com.iab.omid.sampleapp

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iab.omid.sampleapp.MainActivity.Companion.VAST_DEMO_URL
import com.iab.omid.sampleapp.player.CriteoVideoAdConfiguration
import com.iab.omid.sampleapp.player.CriteoVideoAdLogCategory
import com.iab.omid.sampleapp.player.CriteoVideoAdWrapper

/**
 * A fragment demonstrating video ads in a feed/list context.
 *
 * This fragment displays a RecyclerView with video ad items that automatically
 * play when at least 50% visible and pause when less than 50% visible.
 * This simulates a social media feed experience where videos autoplay as
 * the user scrolls through content.
 */
class FeedVideoPlayerFragment : Fragment() {

    private var recyclerView: RecyclerView? = null
    private var adapter: FeedAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_feed_video_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = "Feed Video Ad Example"

        setupRecyclerView(view)
    }

    override fun onResume() {
        super.onResume()

        // Initial visibility check after layout
        view?.post { checkVideoVisibility() }
    }

    override fun onPause() {
        super.onPause()
        adapter?.pauseAllVideos()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter?.cleanupVideos()
        recyclerView = null
        adapter = null
    }

    private fun setupRecyclerView(view: View) {
        adapter = FeedAdapter()

        recyclerView = view.findViewById(R.id.feedRecyclerView)
        recyclerView?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FeedVideoPlayerFragment.adapter

            // Add scroll listener to handle visibility-based auto-play/pause
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    checkVideoVisibility()
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        checkVideoVisibility()
                    }
                }
            })
        }
    }

    /**
     * Checks visibility of the video ad item and plays/pauses based on 50% visibility threshold.
     */
    private fun checkVideoVisibility() {
        val layoutManager = recyclerView?.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()

        for (i in firstVisible..lastVisible) {
            val viewHolder = recyclerView?.findViewHolderForAdapterPosition(i) ?: continue
            if (viewHolder is FeedAdapter.VideoAdViewHolder) {
                val visibilityPercentage = calculateVisibilityPercentage(viewHolder.itemView)
                viewHolder.onVisibilityChanged(visibilityPercentage >= 50)
            }
        }
    }

    /**
     * Calculates what percentage of a view is visible within the RecyclerView bounds.
     *
     * @param view The view to check visibility for.
     * @return Visibility percentage (0-100).
     */
    private fun calculateVisibilityPercentage(view: View): Int {
        val recyclerBounds = Rect()
        recyclerView?.getGlobalVisibleRect(recyclerBounds) ?: return 0

        val viewBounds = Rect()
        view.getGlobalVisibleRect(viewBounds)

        if (viewBounds.isEmpty || view.height == 0) return 0

        val visibleHeight = minOf(viewBounds.bottom, recyclerBounds.bottom) -
                maxOf(viewBounds.top, recyclerBounds.top)

        return if (visibleHeight > 0) (visibleHeight * 100) / view.height else 0
    }

    companion object {
        private const val TAG = "FeedVideoPlayerFragment"
    }

    /**
     * Sealed class representing different types of feed items.
     */
    private sealed class FeedItem {
        data class Content(val title: String, val body: String) : FeedItem()
        data class VideoAd(val vastUrl: String) : FeedItem()
    }

    /**
     * Example RecyclerView adapter for feed items with embedded video ads.
     */
    private inner class FeedAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_TYPE_CONTENT = 0
        private val VIEW_TYPE_VIDEO_AD = 1

        // Cache video players by URL to persist state across ViewHolder recycling
        private val videoAdWrappers = mutableMapOf<String, CriteoVideoAdWrapper>()
        private val videoAdViewHolders = mutableSetOf<VideoAdViewHolder>()

        private val items: List<FeedItem> = listOf(
            FeedItem.Content(
                title = "Welcome to the Feed",
                body = "This is a demo of a social media-style feed with an embedded video ad."
            ),
            FeedItem.Content(
                title ="Sample Content",
                body = "This feed contains sample content items to simulate a real-world social media feed experience."
            ),
            FeedItem.Content(
                title = "Scroll Down to See Video Ad",
                body = "Scroll down to see the video ad appear inline with other content in the feed."
            ),
            FeedItem.Content(
                title = "VAST Support",
                body = "The video player supports VAST (Video Ad Serving Template) for standardized video ad delivery."
            ),
            FeedItem.Content(
                title = "What is VAST",
                body = "VAST is an XML-based standard developed for serving video ads across different platforms and players."
            ),
            FeedItem.Content(
                title = "How It Works",
                body = "The video ad below will automatically play when at least 50% of it is visible on screen, and pause when less than 50% is visible."
            ),
            FeedItem.Content(
                title = "Beacon Tracking",
                body = "Impression, quartile, and completion beacons are automatically fired as the user watches the video the first time through. Other VAST tracking events like clicks and pause/play are also supported."
            ),
            FeedItem.Content(
                title = "All beacons should be fired only once",
                body = "Except for the interaction beacons that should be fired every time the user interacts with these actions (click, mute, unmute, resume, pause)."
            ),
            FeedItem.Content(
                title = "OMID Integration",
                body = "The video player includes Open Measurement (OMID) tracking for viewability measurement, ensuring accurate ad metrics."
            ),
            FeedItem.VideoAd(vastUrl = VAST_DEMO_URL),
            FeedItem.Content(
                title = "Customizable Experience",
                body = "The video player can be customized with different configurations, such as auto-load behavior and mute settings."
            ),
            FeedItem.Content(
                title = "Closed Captioning",
                body = "The player supports closed captioning if provided in the VAST response. The Player UI includes controls for enabling/disabling captions."
            ),
            FeedItem.Content(
                title = "Click Through Support",
                body = "Click-through URLs defined in the VAST response are supported, allowing users to interact with the ad and be directed to a web url."
            ),
            FeedItem.Content(
                title = "Performance Optimizations",
                body = "The video player is optimized for smooth playback and minimal impact on scrolling performance in a feed context."
            ),
            FeedItem.Content(
                title = "Logging Support",
                body = "Logging is available for video playback events and OMID interactions to aid in debugging and monitoring."
            ),
            FeedItem.Content(
                title = "Video Ad Integration",
                body = "Integrating video ads into a feed enhances user engagement and provides monetization opportunities within content streams."
            ),
            FeedItem.Content(
                title = "Thank You for Watching",
                body = "We appreciate you taking the time to explore this feed video ad example."
            ),
            FeedItem.Content(
                title = "Demo Purpose",
                body = "We hope it demonstrates some of the capabilities of our video ad player."
            ),
            FeedItem.Content(
                title = "Demo Code",
                body = "This demo app's source code is available for reference and further exploration."
            )
        )

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is FeedItem.Content -> VIEW_TYPE_CONTENT
            is FeedItem.VideoAd -> VIEW_TYPE_VIDEO_AD
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerView.ViewHolder = when (viewType) {
            VIEW_TYPE_VIDEO_AD -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed_video, parent, false)
                VideoAdViewHolder(view).also { videoAdViewHolders.add(it) }
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feed_content, parent, false)
                ContentViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is FeedItem.Content -> (holder as ContentViewHolder).bind(item)
                is FeedItem.VideoAd -> (holder as VideoAdViewHolder).bind(item)
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            super.onViewRecycled(holder)
            if (holder is VideoAdViewHolder) {
                // Don't cleanup, just pause - we want to preserve the video state when recycled
                holder.pause()
            }
        }

        override fun getItemCount(): Int = items.size

        fun pauseAllVideos() {
            videoAdWrappers.values.forEach { it.pause() }
            videoAdViewHolders.forEach { it.onVisibilityChanged(false) }
        }

        fun cleanupVideos() {
            videoAdWrappers.values.forEach { it.release() }
            videoAdWrappers.clear()
            videoAdViewHolders.forEach { it.cleanup() }
            videoAdViewHolders.clear()
        }

        inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val titleText: TextView = itemView.findViewById(R.id.contentTitle)
            private val bodyText: TextView = itemView.findViewById(R.id.contentBody)

            fun bind(item: FeedItem.Content) {
                titleText.text = item.title
                bodyText.text = item.body
            }
        }

        inner class VideoAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val videoContainer: FrameLayout = itemView.findViewById(R.id.videoAdContainer)

            private var videoAdWrapper: CriteoVideoAdWrapper? = null
            private var isVideoVisible = false
            private var currentVastUrl: String? = null

            fun bind(item: FeedItem.VideoAd) {
                val newUrl = item.vastUrl

                // If binding to a different URL than what this holder last held, reset state
                if (currentVastUrl != null && currentVastUrl != newUrl) {
                    videoContainer.removeAllViews()
                    videoAdWrapper = null
                    isVideoVisible = false
                }
                currentVastUrl = newUrl

                // Check if we already have a player for this URL in the adapter cache
                var player = videoAdWrappers[newUrl]
                if (player == null) {
                    Log.d(TAG, "Creating new player for $newUrl")

                    player = CriteoVideoAdWrapper.fromUrl(
                        context = itemView.context,
                        vastURL = newUrl,
                        configuration = CriteoVideoAdConfiguration(autoLoad = false, startsMuted = true)
                    )

                    // Configure listeners
                    player.enableLogs = setOf(CriteoVideoAdLogCategory.VIDEO, CriteoVideoAdLogCategory.OMID)
                    player.onVideoLoaded = { Log.d(TAG, "Video ad loaded") }
                    player.onVideoStarted = { Log.d(TAG, "Video ad started") }
                    player.onVideoPaused = { Log.d(TAG, "Video ad paused") }

                    videoAdWrappers[newUrl] = player
                } else {
                    Log.d(TAG, "Reusing existing player for $newUrl")
                }

                videoAdWrapper = player

                // Set layout params
                videoContainer.post {
                    videoAdWrapper?.let {
                        val height = (videoContainer.width * 9.0) / 16.0
                        it.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, height.toInt())
                    }
                }

                // Attach to view hierarchy if not already attached to THIS container
                val parent = player.parent
                if (parent != videoContainer) {
                    if (parent is ViewGroup) {
                        parent.removeView(player)
                    }
                    videoContainer.addView(player)
                }
            }

            fun onVisibilityChanged(isVisible: Boolean) {
                if (isVideoVisible == isVisible) return

                isVideoVisible = isVisible

                if (isVisible) videoAdWrapper?.play() else videoAdWrapper?.pause()
                Log.d(TAG, "Auto ${if (isVisible) "play" else "pause"} triggered")
            }

            fun pause() {
                videoAdWrapper?.pause()
            }

            fun cleanup() {
                videoContainer.removeAllViews()
                videoAdWrapper = null
                isVideoVisible = false
                currentVastUrl = null
            }
        }
    }
}
