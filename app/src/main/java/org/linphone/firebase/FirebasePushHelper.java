/*
 * Copyright (c) 2010-2019 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.firebase;

import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import org.linphone.R;
import org.linphone.core.tools.Log;
import org.linphone.settings.LinphonePreferences;
import org.linphone.utils.PushNotificationUtils;

@Keep
public class FirebasePushHelper implements PushNotificationUtils.PushHelperInterface {
    public FirebasePushHelper() {}

    @Override
    public void init(Context context) {
        Log.i(
                "[Push Notification] firebase push sender id "
                        + context.getString(R.string.gcm_defaultSenderId));
        try {
            FirebaseMessaging.getInstance()
                    .getToken()
                    .addOnCompleteListener(
                            new OnCompleteListener<String>() {
                                @Override
                                public void onComplete(@NonNull Task<String> task) {
                                    if (!task.isSuccessful()) {
                                        Log.i(
                                                "TAG",
                                                "Fetching FCM registration token failed",
                                                task.getException());
                                        return;
                                    }

                                    // Get new FCM registration token
                                    String token = task.getResult();

                                    // Log and toast
                                    Log.i("[Push Notification] firebase token is: " + token);
                                    LinphonePreferences.instance()
                                            .setPushNotificationRegistrationID(token);
                                }
                            });
        } catch (Exception e) {
            Log.e("[Push Notification] firebase not available.");
        }
    }

    @Override
    public boolean isAvailable(Context context) {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        return resultCode == ConnectionResult.SUCCESS;
    }
}
