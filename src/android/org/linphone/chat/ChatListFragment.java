/*
ChatListFragment.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

package org.linphone.chat;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.LinphoneActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.EventLog;
import org.linphone.fragments.FragmentsAvailable;
import org.linphone.ui.SelectableHelper;
import org.linphone.ui.SwipeController;
import org.linphone.ui.SwipeControllerActions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.linphone.fragments.FragmentsAvailable.CHAT_LIST;

/*
* Sources: Linphone + https://enoent.fr/blog/2015/01/18/recyclerview-basics/
* */

public class ChatListFragment extends Fragment implements ContactsUpdatedListener, ChatRoomsAdapter.ChatRoomViewHolder.ClickListener, SelectableHelper.DeleteListener {

	private RecyclerView mChatRoomsList;
	private TextView mNoChatHistory;
	private ImageView mNewDiscussionButton, mBackToCallButton;
	private ChatRoomsAdapter mChatRoomsAdapter;
	private CoreListenerStub mListener;
	private RelativeLayout mWaitLayout;
	private int mChatRoomDeletionPendingCount;
	private ChatRoomListenerStub mChatRoomListener;
	private Context mContext;
	public List<ChatRoom> mRooms;
	private SelectableHelper mSelectionHelper;

	@Override
	public View onCreateView(final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		//We get back all ChatRooms from the LinphoneManager and store them
		mRooms = new ArrayList<>(Arrays.asList(LinphoneManager.getLc().getChatRooms()));

		this.mContext = getActivity().getApplicationContext();
		View view = inflater.inflate(R.layout.chatlist, container, false);

		//Views definition
		mChatRoomsList = view.findViewById(R.id.chatList);
		mWaitLayout = view.findViewById(R.id.waitScreen);
		mNewDiscussionButton = view.findViewById(R.id.new_discussion);
		mBackToCallButton = view.findViewById(R.id.back_in_call);

		//Creation and affectation of adapter to the RecyclerView
		mSelectionHelper = new SelectableHelper(view, this);

        mChatRoomsAdapter = new ChatRoomsAdapter(mContext, R.layout.chatlist_cell, mRooms,this, mSelectionHelper);
		mChatRoomsList.setAdapter(mChatRoomsAdapter);

		mSelectionHelper.setAdapter(mChatRoomsAdapter);
		mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);

		//Initialize the LayoutManager
		RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(mContext);
		mChatRoomsList.setLayoutManager(layoutManager);
		mWaitLayout.setVisibility(View.GONE);





		//All commentend code below, until line 145, have to be uncommented to allow swipe actions.

		//Actions allowed by swipe buttons

		final SwipeController swipeController = new SwipeController(new SwipeControllerActions() {
//			@Override
//			public void onLeftClicked(int position) {
//				super.onLeftClicked(position);
//			}

			@Override
			public void onRightClicked(int position) {
				mChatRoomsAdapter.removeItem(position);
				mChatRoomsAdapter.notifyDataSetChanged();
			}
		});

		//Initialize swipe detection


		ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeController);
		itemTouchHelper.attachToRecyclerView(mChatRoomsList);

		//Add swipe buttons
		mChatRoomsList.addItemDecoration(new RecyclerView.ItemDecoration() {
			@Override
			public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
				swipeController.onDraw(c);
			}
		});


		// Buttons onClickListeners definitions



		mNewDiscussionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().goToChatCreator(null, null, null, false, null);
			}
		});

		mBackToCallButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				LinphoneActivity.instance().resetClassicMenuLayoutAndGoBackToCallIfStillRunning();
			}
		});


		//Update ChatRoomsList on change
		mListener = new CoreListenerStub() {
			@Override
			public void onMessageReceived(Core lc, ChatRoom cr, ChatMessage message) {
				refreshChatRoomsList();
			}

			@Override
			public void onChatRoomStateChanged(Core lc, ChatRoom cr, ChatRoom.State state) {
				if (state == ChatRoom.State.Created) {
					refreshChatRoomsList();
				}
			}
		};

		mChatRoomListener = new ChatRoomListenerStub() {
			@Override
			public void onStateChanged(ChatRoom room, ChatRoom.State state) {
				super.onStateChanged(room, state);
				if (state == ChatRoom.State.Deleted || state == ChatRoom.State.TerminationFailed) {
					mChatRoomDeletionPendingCount -= 1;

					if (state == ChatRoom.State.TerminationFailed) {
						//TODO error message
					}

					if (mChatRoomDeletionPendingCount == 0) {
						mWaitLayout.setVisibility(View.GONE);
						refreshChatRoomsList();
					}
				}
			}
		};

		return view;
	}

	@Override
	public void onItemClicked(int position) {
		if (mChatRoomsAdapter.isEditionEnabled()) {
			mChatRoomsAdapter.toggleSelection(position);

		}else{
			ChatRoom room = (ChatRoom) mChatRoomsAdapter.getItem(position);
			LinphoneActivity.instance().goToChat(room.getPeerAddress().asString(),null);
		}
	}

	@Override
	public boolean onItemLongClicked(int position) {
		if (mChatRoomsAdapter.isEditionEnabled()!=true) {
			//Start selection mode
			mSelectionHelper.enterEditionMode();
		}
		mChatRoomsAdapter.toggleSelection(position);
		return true;
	}



	//Existing functions before RecyclerView conversion

	private void refreshChatRoomsList() {
		mChatRoomsAdapter.refresh();
		//mNoChatHistory.setVisibility(mChatRoomsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
	}

	public void displayFirstChat() {
		ChatRoomsAdapter adapter = (ChatRoomsAdapter)mChatRoomsList.getAdapter();
		if (adapter != null && adapter.getItemCount() > 0) {
			ChatRoom room = (ChatRoom) adapter.getItem(0);
			LinphoneActivity.instance().goToChat(room.getPeerAddress().asStringUriOnly(), null);
		} else {
			LinphoneActivity.instance().displayEmptyFragment();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		ContactsManager.addContactsListener(this);

		if (LinphoneManager.getLc().getCallsNb() > 0) {
			mBackToCallButton.setVisibility(View.VISIBLE);
		} else {
			mBackToCallButton.setVisibility(View.GONE);
		}

		if (LinphoneActivity.isInstanciated()) {
			LinphoneActivity.instance().selectMenu(FragmentsAvailable.CHAT_LIST);
			LinphoneActivity.instance().hideTabBar(false);
		}

		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.addListener(mListener);
		}

		refreshChatRoomsList();
	}

	@Override
	public void onPause() {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		if (lc != null) {
			lc.removeListener(mListener);
		}
		ContactsManager.removeContactsListener(this);
		mChatRoomsAdapter.clear();
		super.onPause();

	}

	@Override
	public void onDeleteSelection(Object[] objectsToDelete) {
		Core lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
		mChatRoomDeletionPendingCount = objectsToDelete.length;
		for (Object obj : objectsToDelete) {
			ChatRoom room = (ChatRoom)obj;

			for (EventLog eventLog : room.getHistoryEvents(0)) {
				if (eventLog.getType() == EventLog.Type.ConferenceChatMessage) {
					ChatMessage message = eventLog.getChatMessage();
					if (message.getAppdata() != null && !message.isOutgoing()) {
						File file = new File(message.getAppdata());
						if (file.exists()) {
							file.delete(); // Delete downloaded file from incoming message that will be deleted
						}
					}
				}
			}

			room.addListener(mChatRoomListener);
			lc.deleteChatRoom(room);
		}
		if (mChatRoomDeletionPendingCount > 0) {
			mWaitLayout.setVisibility(View.VISIBLE);
		}
	}


		@Override
	public void onContactsUpdated() {
		if (!LinphoneActivity.isInstanciated() || LinphoneActivity.instance().getCurrentFragment() != CHAT_LIST)
			return;

		ChatRoomsAdapter adapter = (ChatRoomsAdapter) mChatRoomsList.getAdapter();
		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}



}

