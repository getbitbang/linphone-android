package org.linphone.call;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.content.LocalBroadcastManager;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.linphone.LinphoneManager;
import org.linphone.LinphonePreferences;
import org.linphone.LinphoneService;
import org.linphone.LinphoneUtils;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.LinphoneContact;
import org.linphone.core.Address;
import org.linphone.core.Call;
import org.linphone.core.CallParams;
import org.linphone.core.Conference;
import org.linphone.core.ConferenceParams;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Reason;
import org.linphone.mediastream.Log;

import java.util.List;

import static org.linphone.call.LinphoneConnectionService.EXT_TO_CS_END_CALL;
import static org.linphone.call.LinphoneConnectionService.EXT_TO_CS_HOLD_CALL;
import static org.linphone.call.LinphoneConnectionService.EXT_TO_CS_HOLD_CONFERENCE;

/*This class contains all methods for TelecomManager to receive, send calls to the LinphoneConnectionService class.
In that extent, it contains methods from the liblinphone sdk to manage calls (Call class from linphone) handled by it, the LinphoneConnectionService
handles the native part (Connection class).*/

public class TelecomManagerHelper {
    private Call mCall = null;
    private String mCallId = null;
    private TelecomManager telecomManager;
    private boolean alreadyAcceptedOrDeniedCall;
    private PhoneAccountHandle phoneAccountHandle;
    private final int INCALL = 1;
    private final int OUTGOING = 2;
    private final int END = 3;
    private final int CURRENT = 4;
    private final int HELD = 5;
    private final boolean PAUSE = false;
    private final boolean RESUME = true;
    private boolean mBroadcastHold = false;
    private CoreListenerStub mListener;
    public static String TAG = "TelecomManagerHelper";
    private Conference mConference = null;


    //Initiates the telecomManager and dependencies which are needed to handle calls.
    @TargetApi(Build.VERSION_CODES.M)
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public TelecomManagerHelper() {


        telecomManager = (TelecomManager) LinphoneManager.getInstance().getContext().getSystemService(Context.TELECOM_SERVICE);

        if (LinphoneManager.getInstance().getLinPhoneAccount() == null) {
            LinphoneManager.getInstance().setLinPhoneAccount();
        }
        phoneAccountHandle = LinphoneManager.getInstance().getLinPhoneAccount().getAccountHandler();
    }

    public TelecomManager getTelecomManager() {
        return telecomManager;
    }

    public PhoneAccountHandle getPhoneAccountHandle() {
        return phoneAccountHandle;
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startIncall() {
        mBroadcastHold = false;
        alreadyAcceptedOrDeniedCall = false;

        lookupCall(INCALL);


        if (mCall == null) {
            //The incoming call no longer exists.
            Log.d("Couldn't find incoming call");
            return;
        }
        setListenerIncall(mCall);

        String strContact = null;
        Address address = mCall.getRemoteAddress();
        LinphoneContact contact = ContactsManager.getInstance().findContactFromAddress(address);
        if (contact != null) {
            strContact = contact.getFullName();
        }
        String strAddress = address.asStringUriOnly();



        //Define extras needed to pass datas to LinphoneConnectionService
        Bundle extras = new Bundle();
        final Bundle b = new Bundle();


        extras.putString(LinphoneConnectionService.EXT_TO_CS_CALL_ID, mCall.getCallLog().getCallId());
        extras.putString(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, strAddress);


        if (strContact != null) {
            b.putString(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, strContact);
            extras.putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, b);
        }


        //Handle particular case if call happens with a paused call currently handled
        if (LinphoneManager.getLc().getCalls().length == 2) {
            Call nextCall = mCall;
            //At this point, mCall gets the current paused call if it is
            lookupCall(HELD);
            if (nextCall != mCall) {
                //If mCall differs from nextCall, 1st call is held
                mCallId = mCall.getCallLog().getCallId();
                mBroadcastHold = false;
                //Unhold current call to allow ConnectionService to handle the 2nd call and stay beyond LinphoneActivity
                sendToCS(EXT_TO_CS_HOLD_CALL);
            }

            //Same as previously, for conference held
        } else if (LinphoneManager.getLc().getConference() != null && !LinphoneManager.getLc().isInConference()) {
            mBroadcastHold = false;
            sendToCS(EXT_TO_CS_HOLD_CONFERENCE);
        }

        /*Send the call to LinphoneConnectionService, received by onCreateIncomingConnection
        The native UI should show from here*/

        telecomManager.addNewIncomingCall(phoneAccountHandle, extras);
        registerCallScreenReceiver();
    }

    @SuppressLint("MissingPermission")
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startOutgoingCall() {

        mBroadcastHold = false;
        lookupCall(OUTGOING);

        if (mCall == null) {
            //The incoming call no longer exists.
            Log.d("Couldn't find incoming call");
            return;
        }
        setListenerOutgoing(mCall);

        Bundle extras = new Bundle();
        extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_AUDIO_ONLY);
        if (phoneAccountHandle != null) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        }


        Address address = mCall.getRemoteAddress();
        String strAddress = address.asStringUriOnly();

        //Send the call to LinphoneConnectionService, received by onCreateOutgoingConnection
        telecomManager.placeCall(Uri.parse(strAddress), extras);

        //Set the broadcast receiver for push actions in the native UI
        registerCallScreenReceiver();
    }


    //The id is only required for LinphoneConnectionService
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void stopCallById (String callId){
        mCallId = callId;
        mCall=getCallById(mCallId);
        sendToCS(LinphoneConnectionService.EXT_TO_CS_END_CALL);
        if (mCall != null) {
            LinphoneManager.getLc().terminateCall(mCall);
        } else {
            //The call no longer exists.
            Log.d("Couldn't find call");
            return;
        }
        if (LinphoneManager.getLc().getCalls().length == 0) {
            unRegisterCallScreenReceiver();
            LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
        }

    }



    private void setListenerIncall (Call call){
        mListener = new CoreListenerStub() {
            @TargetApi(Build.VERSION_CODES.P)
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
                if (Call.State.End == state || Call.State.Released == state) {

                    stopCallById(call.getCallLog().getCallId());

                } else if (state == Call.State.StreamsRunning) {
                    Log.e("CallIncommingActivity - onCreate -  State.StreamsRunning - speaker = " + LinphoneManager.getInstance().isSpeakerEnabled());
                    // The following should not be needed except some devices need it (e.g. Galaxy S).
                } else if (call == mCall && (Call.State.PausedByRemote == state) ) {
                    Toast.makeText(LinphoneManager.getInstance().getContext(),"Vous êtes en attente",Toast.LENGTH_LONG).show();
                } else if (state == Call.State.UpdatedByRemote) {
                    // If the correspondent proposes video while audio call
                    mCallId=call.getCallLog().getCallId();
                    boolean remoteVideo = call.getRemoteParams().videoEnabled();
                    boolean localVideo = call.getCurrentParams().videoEnabled();
                    boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
                    if (remoteVideo && localVideo && autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
                        sendToCS(EXT_TO_CS_END_CALL);
                    } else if (!autoAcceptCameraPolicy) {
                        //Refuse video
                        if (call == null) {
                            return;
                        }
                        CallParams params = LinphoneManager.getLc().createCallParams(call);
                        LinphoneManager.getLc().acceptCallUpdate(call, params);
                    }
                }
            }
        };

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
    }

    private void setListenerOutgoing (Call call){
        mListener = new CoreListenerStub() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void onCallStateChanged(Core lc, Call call, Call.State state, String message) {
                if (call == mCall && Call.State.Connected == state && call.getDir() == Call.Dir.Outgoing) {
                    if (!LinphoneActivity.isInstanciated()) {
                        return;
                    }
                    mCallId=call.getCallLog().getCallId();
                    sendToCS(LinphoneConnectionService.EXT_TO_CS_ESTABLISHED);
                    return;
                } else if (state == Call.State.Error) {
                    // Convert Core message for internalization
                    if (call.getErrorInfo().getReason() == Reason.Declined) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_call_declined), Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    } else if (call.getErrorInfo().getReason() == Reason.NotFound) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_user_not_found), Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    } else if (call.getErrorInfo().getReason() == Reason.NotAcceptable) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_incompatible_media), Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    } else if (call.getErrorInfo().getReason() == Reason.Busy) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_user_busy), Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    } else if (message != null) {
                        displayCustomToast((R.string.error_unknown) + " - " + message, Toast.LENGTH_SHORT);
                        stopCallById(call.getCallLog().getCallId());
                    }
                }else if (state == Call.State.End && call == mCall) {
                    stopCallById(call.getCallLog().getCallId());
                    // Convert Core message for internalization
                    if (call.getErrorInfo().getReason() == Reason.Declined) {
                        displayCustomToast(LinphoneManager.getInstance().getContext().getString(R.string.error_call_declined), Toast.LENGTH_SHORT);
                    }
                } else if (call == mCall && (Call.State.PausedByRemote == state) ) {
                    Toast.makeText(LinphoneManager.getInstance().getContext(),"Vous êtes en attente",Toast.LENGTH_LONG).show();
                } else if (state == Call.State.UpdatedByRemote) {
                    // If the correspondent proposes video while audio call
                    mCallId=call.getCallLog().getCallId();
                    boolean remoteVideo = call.getRemoteParams().videoEnabled();
                    boolean autoAcceptCameraPolicy = LinphonePreferences.instance().shouldAutomaticallyAcceptVideoRequests();
                    if (remoteVideo && autoAcceptCameraPolicy && !LinphoneManager.getLc().isInConference()) {
                        //End the native UI, Linphone Video fragment is launched.
                        sendToCS(EXT_TO_CS_END_CALL);
                    } else if (!autoAcceptCameraPolicy) {
                        //Refuse video

                        if (call == null) {
                            return;
                        }

                        CallParams params = LinphoneManager.getLc().createCallParams(call);
                        LinphoneManager.getLc().acceptCallUpdate(call, params);
                   }
                }

                if (LinphoneManager.getLc().getCallsNb() == 0) {
                    return;
                }
            }
        };

        Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.addListener(mListener);
        }
    }


    //Send intents to LinphoneConnectionService
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void sendToCS (int action) {
        Intent intent = new Intent(LinphoneConnectionService.EXT_TO_CS_BROADCAST);
        intent.putExtra(LinphoneConnectionService.EXT_TO_CS_ACTION, action);
        if (action != EXT_TO_CS_HOLD_CONFERENCE) {
            intent.putExtra(LinphoneConnectionService.EXT_TO_CS_CALL_ID, mCallId);
        }
        if ((action == LinphoneConnectionService.EXT_TO_CS_HOLD_CALL || action == EXT_TO_CS_HOLD_CONFERENCE)&& mBroadcastHold) {
            intent.putExtra(LinphoneConnectionService.EXT_TO_CS_HOLD_STATE, true);
        }

        LocalBroadcastManager.getInstance(LinphoneManager.getInstance().getContext()).sendBroadcast(intent);
    }

    private void lookupCall(int type) {

        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<Call> calls = LinphoneUtils.getCalls(LinphoneManager.getLc());

            for (Call call : calls) {
                switch (type) {
                    case INCALL:
                        if (Call.State.IncomingReceived == call.getState()) {
                            mCall = call;
                            break;
                        }

                    case END:
                        if (call.getState() == Call.State.Released) {
//                        if ((call.getState() == Call.State.End) || (call.getState() == Call.State.Error )|| (call.getState() == Call.State.Released)) {
                            mCall = call;
                            break;
                        }
                    case OUTGOING:
                        if ((Call.State.OutgoingInit == call.getState()) || (Call.State.OutgoingProgress == call.getState())|| (Call.State.OutgoingRinging == call.getState())) {
                            mCall = call;
                            break;
                        }
                    case CURRENT:
                        if (Call.State.StreamsRunning == call.getState()) {
                            mCall = call;
                            break;
                        }
                    case HELD:
                        if (Call.State.Paused == call.getState()) {
                            mCall = call;
                            break;
                        }

                }
            }
        }
    }

    //BroadcastReceiver which role is to receive callback from user's action on incall/current call screen buttons
    //and provide actions
    private BroadcastReceiver mCallScreenEventsReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.P)
        @Override
        public void onReceive(Context context, Intent intent) {
            int action = intent.getIntExtra(LinphoneConnectionService.CS_TO_EXT_ACTION, -1);
            String callId = intent.getStringExtra(LinphoneConnectionService.CS_TO_EXT_CALL_ID);
            boolean isConference = intent.getBooleanExtra(LinphoneConnectionService.CS_TO_EXT_IS_CONFERENCE, false);

            if(!isConference){
                mCall = getCallById(callId);
            }
            android.util.Log.d(TAG, "callScreenEventsReceiver: action: "+action+" | callId: "+callId);
            if (mCall == null){
                return;
            }
            switch (action){
                case LinphoneConnectionService.CS_TO_EXT_ANSWER:
                    answer();
                    break;
                case LinphoneConnectionService.CS_TO_EXT_REJECT:
                    stopCallById(callId);
                    if (LinphoneManager.getLc().getCalls().length == 1) {
                        lookupCall(HELD);
                        if (mCall != null && mCall.getState() == Call.State.Paused) {
                            mCallId = mCall.getCallLog().getCallId();
                            mBroadcastHold = true;
                            sendToCS(EXT_TO_CS_HOLD_CALL);
                        }
                    } else if (LinphoneManager.getLc().getConference() != null && !LinphoneManager.getLc().isInConference()) {
                        mBroadcastHold = true;
                        sendToCS(EXT_TO_CS_HOLD_CONFERENCE);
                    }
                    break;
                case LinphoneConnectionService.CS_TO_EXT_DISCONNECT:
                    stopCallById(callId);
                    break;
                case LinphoneConnectionService.CS_TO_EXT_ABORT:
                    stopCallById(callId);
                    break;
                case LinphoneConnectionService.CS_TO_EXT_HOLD:
                    if (isConference){
                        pauseOrResumeConference(PAUSE);
                    }else{
                        pauseOrResumeCall(mCall, PAUSE);
                    }
                    break;
                case LinphoneConnectionService.CS_TO_EXT_UNHOLD:
                    if (isConference){
                        pauseOrResumeConference(RESUME);
                    }else{
                        pauseOrResumeCall(mCall, RESUME);
                    }
                    break;
                case LinphoneConnectionService.CS_TO_EXT_ADD_TO_CONF:
                    if (mConference == null){
                        startConference();
                    }
                    LinphoneManager.getLc().addToConference(getCallById(callId));
                    break;
                case LinphoneConnectionService.CS_TO_EXT_REMOVE_FROM_CONF:

                    Call temp = getCallById(callId);
                    LinphoneManager.getLc().removeFromConference(temp);
                    pauseOrResumeCall(temp, PAUSE);

                    break;
            }
        }
    };


    public Call getCallById(String callId) {
        if (LinphoneManager.getLcIfManagerNotDestroyedOrNull() != null) {
            List<Call> calls = LinphoneUtils.getCalls(LinphoneManager.getLc());
            for (Call call : calls) {
                if (callId.equals(call.getCallLog().getCallId())) {
                    return call;
                }
            }
        }
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void registerCallScreenReceiver(){
        android.util.Log.d(TAG, "registerCallScreenReceiver...");
        LocalBroadcastManager.getInstance(LinphoneManager.getInstance().getContext()).registerReceiver(
                mCallScreenEventsReceiver, new IntentFilter(LinphoneConnectionService.CS_TO_EXT_BROADCAST));
    }

    private void unRegisterCallScreenReceiver(){
        LocalBroadcastManager.getInstance(LinphoneManager.getInstance().getContext()).unregisterReceiver(mCallScreenEventsReceiver);
        android.util.Log.d(TAG, "unRegisterCallScreenReceiver...");
    }



    private void answer() {
        if (alreadyAcceptedOrDeniedCall) {
            return;
        }
        alreadyAcceptedOrDeniedCall = true;

        CallParams params = LinphoneManager.getLc().createCallParams(mCall);

        boolean isLowBandwidthConnection = !LinphoneUtils.isHighBandwidthConnection(LinphoneService.instance().getApplicationContext());

        if (params != null) {
            params.enableLowBandwidth(isLowBandwidthConnection);
        } else {
            Log.e("Could not create call params for call");
        }

        if (params == null || !LinphoneManager.getInstance().acceptCallWithParams(mCall, params)) {
            // the above method takes care of Samsung Galaxy S
            Toast.makeText(LinphoneManager.getInstance().getContext(), R.string.couldnt_accept_call, Toast.LENGTH_LONG).show();
        } else {
            if (!LinphoneActivity.isInstanciated()) {
                return;
            }
            LinphoneManager.getInstance().routeAudioToReceiver();
            LinphoneManager.getLc().acceptCall(mCall);
        }
    }




    public void displayCustomToast(final String message, final int duration) {
        LayoutInflater inflater = LinphoneActivity.instance().getLayoutInflater();
        View layout = inflater.inflate(R.layout.toast, (ViewGroup) LinphoneActivity.instance().findViewById(R.id.toastRoot));

        TextView toastText = (TextView) layout.findViewById(R.id.toastMessage);
        toastText.setText(message);

        final Toast toast = new Toast(LinphoneManager.getInstance().getContext());
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.setDuration(duration);
        toast.setView(layout);
        toast.show();
    }

    private void pauseOrResumeCall(Call call, Boolean resume) {
        Core lc = LinphoneManager.getLc();
        if (call != null && LinphoneManager.getLc().getCurrentCall() == call && !resume) {
            lc.pauseCall(call);
        } else if (call != null) {
            Call.State test = call.getState();
            if ((call.getState() == Call.State.Paused)&& resume) {
                lc.resumeCall(call);
            }
        }
    }

    public void startConference() {
        ConferenceParams mConfParams= LinphoneManager.getLc().createConferenceParams();
        mConference= LinphoneManager.getLc().createConferenceWithParams(mConfParams);
    }

    public void pauseOrResumeConference(boolean resume) {
        Core lc = LinphoneManager.getLc();
            if (lc.isInConference() && !resume) {
                lc.leaveConference();
            } else if (resume){
                lc.enterConference();
            }
    }

}