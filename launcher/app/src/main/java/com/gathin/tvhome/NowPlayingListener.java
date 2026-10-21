package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Does nothing itself. Android lets an app read the active media sessions only if it holds notification access,
 * and that access belongs to a listener service (see NowPlayingCard). It never reads a notification.
 */
public class NowPlayingListener extends NotificationListenerService {
}
