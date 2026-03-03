package com.sun.alasbrowser.media

import android.content.Context
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

class MediaRouterManager(private val context: Context) {
    
    private var mediaRouter: MediaRouter? = null
    private var mediaRouteSelector: MediaRouteSelector? = null
    private var castContext: CastContext? = null
    private var sessionManager: SessionManager? = null
    private var currentCastSession: CastSession? = null
    
    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
  
            currentCastSession = session
            onCastSessionChanged(true)
        }
        
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
 
            currentCastSession = session
            onCastSessionChanged(true)
        }
        
        override fun onSessionEnded(session: CastSession, error: Int) {
   
            currentCastSession = null
            onCastSessionChanged(false)
        }
        
        override fun onSessionSuspended(session: CastSession, reason: Int) {
       
        }
        
        override fun onSessionStarting(session: CastSession) {

        }
        
        override fun onSessionStartFailed(session: CastSession, error: Int) {
       
        }
        
        override fun onSessionEnding(session: CastSession) {
        
        }
        
        override fun onSessionResuming(session: CastSession, sessionId: String) {
    
        }
        
        override fun onSessionResumeFailed(session: CastSession, error: Int) {
       
        }
    }
    
    var onCastSessionChanged: (Boolean) -> Unit = {}
    
    fun initialize() {
        try {
            val availability = GoogleApiAvailability.getInstance()
            val result = availability.isGooglePlayServicesAvailable(context)
            
            if (result == ConnectionResult.SUCCESS) {
                try {
                    castContext = CastContext.getSharedInstance(context)
                    sessionManager = castContext?.sessionManager
                    sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)

                } catch (e: Exception) {
             
                }
            } else {
               
            }
            
            mediaRouter = MediaRouter.getInstance(context)
            
        } catch (e: Exception) {
        
        }
    }
    
    fun castVideo(videoUrl: String, title: String = "Video", thumbnailUrl: String? = null) {
        currentCastSession?.let { session ->
            try {
              
            } catch (e: Exception) {
           
            }
        } ?: run {
            
        }
    }
    
    fun isCasting(): Boolean = currentCastSession != null
    
    fun getCastContext(): CastContext? = castContext
    
    fun getMediaRouter(): MediaRouter? = mediaRouter
    
    fun release() {
        sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
        currentCastSession = null
        sessionManager = null
        castContext = null
        mediaRouter = null
    }
}
