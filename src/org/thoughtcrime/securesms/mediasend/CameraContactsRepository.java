package org.thoughtcrime.securesms.mediasend;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.thoughtcrime.securesms.contacts.ContactsDatabase;
import org.thoughtcrime.securesms.database.Address;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.model.ThreadRecord;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles retrieving the data to be shown in {@link CameraContactSelectionFragment}.
 */
class CameraContactsRepository {

  private static final int RECENT_MAX = 25;

  private final Context           context;
  private final ThreadDatabase    threadDatabase;
  private final GroupDatabase     groupDatabase;
  private final ContactsDatabase  contactsDatabase;
  private final RecipientDatabase recipientDatabase;

  CameraContactsRepository(@NonNull Context context) {
    this.context           = context.getApplicationContext();
    this.threadDatabase    = DatabaseFactory.getThreadDatabase(context);
    this.groupDatabase     = DatabaseFactory.getGroupDatabase(context);
    this.contactsDatabase  = DatabaseFactory.getContactsDatabase(context);
    this.recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
  }

  void getCameraContacts(@NonNull Callback<CameraContacts> callback) {
    getCameraContacts("", callback);
  }

  void getCameraContacts(@NonNull String query, @NonNull Callback<CameraContacts> callback) {
    SignalExecutors.BOUNDED.execute(() -> {
      List<Recipient> recents  = getRecents(query);
      List<Recipient> contacts = getContacts(query);
      List<Recipient> groups   = getGroups(query);

      callback.onComplete(new CameraContacts(recents, contacts, groups));
    });
  }


  @WorkerThread
  private @NonNull List<Recipient> getRecents(@NonNull String query) {
    if (!TextUtils.isEmpty(query)) {
      return Collections.emptyList();
    }

    List<Recipient> recipients = new ArrayList<>(RECENT_MAX);

    try (ThreadDatabase.Reader threadReader = threadDatabase.readerFor(threadDatabase.getRecentPushConversationList(RECENT_MAX))) {
      ThreadRecord threadRecord;
      while ((threadRecord = threadReader.getNext()) != null) {
        recipients.add(threadRecord.getRecipient().resolve());
      }
    }

    return recipients;
  }

  @WorkerThread
  private @NonNull List<Recipient> getContacts(@NonNull String query) {
    List<Recipient> recipients = new ArrayList<>();

    try (Cursor cursor = contactsDatabase.queryTextSecureContacts(query)) {
      while (cursor.moveToNext()) {
        Recipient recipient = Recipient.external(context, cursor.getString(1));
        recipients.add(recipient);
      }
    }

    return recipients;
  }

  @WorkerThread
  private @NonNull List<Recipient> getGroups(@NonNull String query) {
    if (TextUtils.isEmpty(query)) {
      return Collections.emptyList();
    }

    List<Recipient> recipients = new ArrayList<>();

    try (GroupDatabase.Reader reader = groupDatabase.getGroupsFilteredByTitle(query)) {
      GroupDatabase.GroupRecord groupRecord;
      while ((groupRecord = reader.getNext()) != null) {
        RecipientId recipientId = recipientDatabase.getOrInsertFromGroupId(groupRecord.getEncodedId());
        recipients.add(Recipient.resolved(recipientId));
      }
    }

    return recipients;
  }

  interface Callback<E> {
    void onComplete(E result);
  }
}