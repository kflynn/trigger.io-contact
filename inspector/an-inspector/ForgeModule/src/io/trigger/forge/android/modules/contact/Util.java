package io.trigger.forge.android.modules.contact;

import io.trigger.forge.android.core.ForgeApp;
import io.trigger.forge.android.core.ForgeLog;
import io.trigger.forge.android.core.ForgeTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Vector;

import android.content.ContentProviderOperation;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.BaseTypes;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Base64;

import com.google.common.base.Joiner;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

interface JSONConverter {
    public void execute(ArrayList<ContentProviderOperation> ops,
                        int backRef, JsonObject obj);
}

class InsertOperation {
    private JsonObject obj;
    private ContentProviderOperation.Builder op;
    private boolean needsAdd;

    public InsertOperation(JsonObject obj, int backRef, String contentType) {
        this.obj = obj;
        this.op =  ContentProviderOperation.newInsert(Data.CONTENT_URI)
                   .withValueBackReference(Data.RAW_CONTACT_ID, backRef)
                   .withValue(Data.MIMETYPE, contentType);
        this.needsAdd = false;
    }

    private String fetch(String key) {
        return fetch(key, this.obj);
    }

    private String fetch(String key, JsonObject obj) {
        JsonElement child = obj.get(key);

        if (child == null) {
            ForgeLog.d("- no key " + key);
            return null;
        }

        String value = child.getAsString();

        if ((value == null) || (value.length() <= 0)) {
            ForgeLog.d("- empty value for " + key);
            return null;
        }

        return value;
    }

    public void withValue(String key, String value) {
        this.op.withValue(key, value);
    }

    public void withValue(String key, int value) {
        this.op.withValue(key, value);
    }

    public void checkField(String field, String key) {
        checkField(field, key, this.obj);
    }

    public void checkField(String field, String key, JsonObject obj) {
        String value = this.fetch(field, obj);

        if (value == null) {
            return;
        }

        ForgeLog.d("- adding op for " + key + ": " + value);
        this.withValue(key, value);
        this.needsAdd = true;
    } 

    public void mapType(HashMap<String, Integer> typeMap,
                        String typeKey, int customType,
                        String labelKey) {
        String objType = this.fetch("type");

        if (objType == null) {
            // Hmm.  No type.  Screw it, do nothing.
            ForgeLog.d("-- type: missing key");
            return;
        }

        // Does this appear in our map?
        Integer mappedType = typeMap.get(objType.toLowerCase());

        if (mappedType != null) {
            ForgeLog.d("-- type " + objType + ": " + mappedType);
            this.op.withValue(typeKey, mappedType);
        }
        else {
            ForgeLog.d("-- custom type " + objType + ": " + 
                       customType + ", setting " + labelKey);
            this.op.withValue(typeKey, customType)
                   .withValue(labelKey, objType);
        }
    }

    public void done(ArrayList<ContentProviderOperation> ops) {
        if (this.needsAdd) {
            ops.add(this.op.build());
        }
    }
}    

class Util {
    public static JsonArray allFields = new JsonArray();
    public static JsonArray allFieldsForAdd = new JsonArray();
    public static HashMap<String, Integer> typeMapOrganization =
        new HashMap<String, Integer>();
    public static HashMap<String, Integer> typeMapPhone =
        new HashMap<String, Integer>();
    public static HashMap<String, Integer> typeMapAddress =
        new HashMap<String, Integer>();
    public static HashMap<String, Integer> typeMapEmail =
        new HashMap<String, Integer>();
    public static HashMap<String, Integer> typeMapIMProtocol =
        new HashMap<String, Integer>();
    public static HashMap<String, Integer> typeMapURL =
        new HashMap<String, Integer>();

    static {
        // allFields is the list of fields relevant when reading contacts.
        //
        // WARNING WARNING WARNING
        // Don't modify allFields unless the field you're adding is 
        // relevant when reading _and_ adding.  If it's just for adding,
        // modify only allFieldsForAdd.

        allFields.add(new JsonPrimitive("nickname"));
        allFields.add(new JsonPrimitive("note"));
        allFields.add(new JsonPrimitive("birthday"));
        allFields.add(new JsonPrimitive("name"));
        allFields.add(new JsonPrimitive("emails"));
        allFields.add(new JsonPrimitive("phoneNumbers"));
        allFields.add(new JsonPrimitive("addresses"));
        allFields.add(new JsonPrimitive("ims"));
        allFields.add(new JsonPrimitive("urls"));
        allFields.add(new JsonPrimitive("organizations"));
        allFields.add(new JsonPrimitive("photos"));

        // allFieldsForAdd is the list of fields relevant when adding
        // contacts. 

        allFieldsForAdd.addAll(allFields);
        allFieldsForAdd.add(new JsonPrimitive("displayName"));

        typeMapOrganization.put("other", Organization.TYPE_OTHER);
        typeMapOrganization.put("work", Organization.TYPE_WORK);

        typeMapPhone.put("assistant", Phone.TYPE_ASSISTANT);
        typeMapPhone.put("callback", Phone.TYPE_CALLBACK);
        typeMapPhone.put("car", Phone.TYPE_CAR);
        typeMapPhone.put("company_main", Phone.TYPE_COMPANY_MAIN);
        typeMapPhone.put("fax_home", Phone.TYPE_FAX_HOME);
        typeMapPhone.put("fax_work", Phone.TYPE_FAX_WORK);
        typeMapPhone.put("home", Phone.TYPE_HOME);
        typeMapPhone.put("isdn", Phone.TYPE_ISDN);
        typeMapPhone.put("main", Phone.TYPE_MAIN);
        typeMapPhone.put("mms", Phone.TYPE_MMS);
        typeMapPhone.put("mobile", Phone.TYPE_MOBILE);
        typeMapPhone.put("other", Phone.TYPE_OTHER);
        typeMapPhone.put("other", Phone.TYPE_OTHER);
        typeMapPhone.put("other_fax", Phone.TYPE_OTHER_FAX);
        typeMapPhone.put("pager", Phone.TYPE_PAGER);
        typeMapPhone.put("radio", Phone.TYPE_RADIO);
        typeMapPhone.put("telex", Phone.TYPE_TELEX);
        typeMapPhone.put("tty_tdd", Phone.TYPE_TTY_TDD);
        typeMapPhone.put("work", Phone.TYPE_WORK);
        typeMapPhone.put("work", Phone.TYPE_WORK);
        typeMapPhone.put("work_mobile", Phone.TYPE_WORK_MOBILE);
        typeMapPhone.put("work_pager", Phone.TYPE_WORK_PAGER);

        typeMapAddress.put("home", StructuredPostal.TYPE_HOME);
        typeMapAddress.put("other", StructuredPostal.TYPE_OTHER);
        typeMapAddress.put("work", StructuredPostal.TYPE_WORK);

        typeMapEmail.put("home", Email.TYPE_HOME);
        typeMapEmail.put("other", Email.TYPE_OTHER);
        typeMapEmail.put("mobile", Email.TYPE_MOBILE);
        typeMapEmail.put("work", Email.TYPE_WORK);

        typeMapIMProtocol.put("aim", Im.PROTOCOL_AIM);
        typeMapIMProtocol.put("msn", Im.PROTOCOL_MSN);
        typeMapIMProtocol.put("yahoo", Im.PROTOCOL_YAHOO);
        typeMapIMProtocol.put("skype", Im.PROTOCOL_SKYPE);
        typeMapIMProtocol.put("qq", Im.PROTOCOL_QQ);
        typeMapIMProtocol.put("google_talk", Im.PROTOCOL_GOOGLE_TALK);
        typeMapIMProtocol.put("icq", Im.PROTOCOL_ICQ);
        typeMapIMProtocol.put("jabber", Im.PROTOCOL_JABBER);
        typeMapIMProtocol.put("netmeeting", Im.PROTOCOL_NETMEETING);

        typeMapURL.put("blog", Website.TYPE_BLOG);
        typeMapURL.put("ftp", Website.TYPE_FTP);
        typeMapURL.put("home", Website.TYPE_HOME);
        typeMapURL.put("homepage", Website.TYPE_HOMEPAGE);
        typeMapURL.put("other", Website.TYPE_OTHER);
        typeMapURL.put("profile", Website.TYPE_PROFILE);
        typeMapURL.put("work", Website.TYPE_WORK);
    }
	
    /**
     * Returns an array of Strings which can be used to limit the columns returned by the data provider.
     * 
     * @param fields the high-level field names we need data for: possible values are in the allFields array.
     */
    private static String[] getProjection(final JsonArray fields) {
        Vector<String> projection = new Vector<String>();
		
        // Columns which must be included for internal uses
        projection.add(ContactsContract.Contacts._ID);
        projection.add(ContactsContract.Data.CONTACT_ID);
        projection.add(ContactsContract.Data.LOOKUP_KEY);
        projection.add(ContactsContract.Data.DISPLAY_NAME);
        projection.add(ContactsContract.Data.MIMETYPE);

        for (JsonElement jsonField : fields) {
            String field = jsonField.getAsString();
            if (field.equals("name")) {
                projection.add(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
                projection.add(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
                projection.add(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
                projection.add(ContactsContract.CommonDataKinds.StructuredName.PREFIX);
                projection.add(ContactsContract.CommonDataKinds.StructuredName.SUFFIX);
                projection.add(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
            } else if (field.equals("nickname")) {
                projection.add(ContactsContract.CommonDataKinds.Nickname.NAME);
            } else if (field.equals("phoneNumbers")) {
                projection.add(ContactsContract.CommonDataKinds.Phone.NUMBER);
                projection.add(ContactsContract.CommonDataKinds.Phone.TYPE);
                projection.add(ContactsContract.CommonDataKinds.Phone.LABEL);
            } else if (field.equals("emails")) {
                projection.add(ContactsContract.CommonDataKinds.Email.DATA);
                projection.add(ContactsContract.CommonDataKinds.Email.TYPE);
                projection.add(ContactsContract.CommonDataKinds.Email.LABEL);
            } else if (field.equals("addresses")) {
                projection.add(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS);
                projection.add(ContactsContract.CommonDataKinds.StructuredPostal.TYPE);
                projection.add(ContactsContract.CommonDataKinds.StructuredPostal.LABEL);
                projection.add(ContactsContract.CommonDataKinds.StructuredPostal.STREET);
                projection.add(ContactsContract.CommonDataKinds.StructuredPostal.CITY);
                projection.add(ContactsContract.CommonDataKinds.StructuredPostal.REGION);
                projection.add(ContactsContract.CommonDataKinds.StructuredPostal.POSTCODE);
                projection.add(ContactsContract.CommonDataKinds.StructuredPostal.COUNTRY);
            } else if (field.equals("ims")) {
                projection.add(ContactsContract.CommonDataKinds.Im.DATA);
                projection.add(ContactsContract.CommonDataKinds.Im.PROTOCOL);
                projection.add(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL);
            } else if (field.equals("organizations")) {
                projection.add(ContactsContract.CommonDataKinds.Organization.COMPANY);
                projection.add(ContactsContract.CommonDataKinds.Organization.TYPE);
                projection.add(ContactsContract.CommonDataKinds.Organization.LABEL);
                projection.add(ContactsContract.CommonDataKinds.Organization.DEPARTMENT);
                projection.add(ContactsContract.CommonDataKinds.Organization.TITLE);
            } else if (field.equals("birthday")) {
                projection.add(ContactsContract.CommonDataKinds.Event.START_DATE);
                projection.add(ContactsContract.CommonDataKinds.Event.TYPE);
            } else if (field.equals("note")) {
                projection.add(ContactsContract.CommonDataKinds.Note.NOTE);
            } else if (field.equals("photos")) {
                projection.add(ContactsContract.CommonDataKinds.Photo.PHOTO);
            } else if (field.equals("urls")) {
                projection.add(ContactsContract.CommonDataKinds.Website.URL);
                projection.add(ContactsContract.CommonDataKinds.Website.TYPE);
                projection.add(ContactsContract.CommonDataKinds.Website.LABEL);
            }
        }
		
        return projection.toArray(new String[projection.size()]);
    }
	
    /**
     * Return the mime-types which correspond to the fields passed in as an argument.
     * 
     * @param fields the high-level field names to return mime-types for; valid values are in allFields.
     */
    private static String[] getMimeTypes(final JsonArray fields) {
        Vector<String> mimeTypes = new Vector<String>();
		
        for (JsonElement jsonField : fields) {
            String field = jsonField.getAsString();
            if (field.equals("name")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
            } else if (field.equals("nickname")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE);
            } else if (field.equals("phoneNumbers")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
            } else if (field.equals("emails")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE);
            } else if (field.equals("addresses")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE);
            } else if (field.equals("ims")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE);
            } else if (field.equals("organizations")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE);
            } else if (field.equals("birthday")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE);
            } else if (field.equals("note")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE);
            } else if (field.equals("photos")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE);
            } else if (field.equals("urls")) {
                mimeTypes.add(ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE);
            }
        }
		
        return mimeTypes.toArray(new String[mimeTypes.size()]);
    }

    /**
     * Return the column index, given its name
     * 
     * @param name column name
     * @param columnMemo mapping of name to index
     * @return integer column index
     */
    private static int getColumnIndex(String name, Map<String, Integer> columnMemo) {
        return columnMemo.get(name); 
    }
	
    /**
     * Return the value of the named column, at the current row
     * @param cursor database cursor into provider results
     * @param name
     * @param columnMemo
     * @return specified value, or an empty string if not found 
     */
    private static String getValue(Cursor cursor, String name, Map<String, Integer> columnMemo) {
        try {
            return cursor.getString(getColumnIndex(name, columnMemo));
        } catch (Exception e) {
            return "";
        }
    }
	
    private static int getValueOrMinusOne(Cursor cursor, String name, Map<String, Integer> columnMemo) {
        String value = getValue(cursor, name, columnMemo);
        if ("" == value) {
            return -1;
        } else {
            return Integer.parseInt(value); 
        }
    }

    private static void 
    addIfPresent(JsonObject obj, String fieldName, 
                 Cursor cursor, Map<String, Integer> columnMemo,
                 String key) {
        String value = getValue(cursor, key, columnMemo);

        if ((value != null) && (value.length() > 0)) {
            obj.addProperty(fieldName, value);
        }
    }
	
    /**
     * For a mapping of contactId to Json contact objects, fill out each contact with the projection of fields
     * (or all fields, if fields is null)
     * @param contacts mapping of contactId to JsonObject contact
     * @param fields array of high-level fields, or null for everything
     * 
     * NB contacts is changed in-place
     */
    public static void populateContacts(final Map<String, JsonObject> contacts, JsonArray fields) {
        if (fields == null) {
            fields = allFields;
        }
        final String[] projection = getProjection(fields);
		
        Joiner joiner = Joiner.on("','").skipNulls();
        String contactIds = "'"+joiner.join(contacts.keySet())+"'";
		
        final String[] mimeTypes = getMimeTypes(fields);
		
        StringBuilder selection = new StringBuilder();
		
        selection.append(ContactsContract.Data.CONTACT_ID + " in ("+contactIds+")");
        if (mimeTypes.length > 0) {
            selection.append(" AND (");
            for (int i = 0; i < mimeTypes.length - 1; i++) {
                selection.append(ContactsContract.Data.MIMETYPE + " = ? OR ");
            }
            selection.append(ContactsContract.Data.MIMETYPE + " = ?)");
        }
		
        Cursor cursor = ForgeApp.getActivity().getContentResolver().query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection.toString(),
            mimeTypes, null);
		
        try {
            if (!cursor.moveToFirst()) {
                return;
            }
            do {
                JsonObject contact = contacts.get(cursor.getString(1));
                if (contact != null) {
                    contactToJSON(cursor, contact, projection);
                }
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
    }
	
    /**
     * Fill out a single contact with data from the columns specified by the fields projection
     *  
     * @param contactId
     * @param fields high-level field names to limit the columns inspected, or null for everything
     * @return the contact object changed in-place
     */
    public static JsonObject contactIdToJsonObject(final String contactId, JsonArray fields) {
        if (fields == null) {
            fields = allFields;
        }
        final String[] projection = getProjection(fields);
        final String[] mimeTypes = getMimeTypes(fields);
		
        StringBuilder selection = new StringBuilder();
		
        selection.append(ContactsContract.Data.CONTACT_ID + " = '"+contactId+"'");
        if (mimeTypes.length > 0) {
            selection.append(" AND (");
            for (int i = 0; i < mimeTypes.length - 1; i++) {
                selection.append(ContactsContract.Data.MIMETYPE + " = ? OR ");
            }
            selection.append(ContactsContract.Data.MIMETYPE + " = ?)");
        }
		
        Cursor cursor = ForgeApp.getActivity().getContentResolver().query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection.toString(),
            mimeTypes, null);
		
        try {
            JsonObject contact = new JsonObject();
            if (!cursor.moveToFirst()) {
                return null;
            }
            do {
                contact = contactToJSON(cursor, contact, projection);
            } while (cursor.moveToNext());
            return contact;
        } finally {
            cursor.close();
        }
    }
	
    // See contactIdToJsonObject
    public static JsonObject contactToJSON(Cursor cursor, JsonObject contact,
                                           String[] projection) {
        Map<String, Integer> columnMemo = new Hashtable<String, Integer>();

        for (int idx=0; idx<projection.length; idx++) {
            columnMemo.put(projection[idx], idx);
        }
		
        addIfPresent(contact, "displayName", cursor, columnMemo,
                     Data.DISPLAY_NAME);
        addIfPresent(contact, "id", cursor, columnMemo,
                     ContactsContract.Contacts._ID);
		
        String mimeType = getValue(cursor, Data.MIMETYPE, columnMemo);
		
        if (mimeType.equals(Nickname.CONTENT_ITEM_TYPE)) {
            addIfPresent(contact, "nickname", cursor, columnMemo,
                         Nickname.NAME);
        } else if (mimeType.equals(Note.CONTENT_ITEM_TYPE)) {
            addIfPresent(contact, "note", cursor, columnMemo,
                         Note.NOTE);
        } else if (mimeType.equals(Event.CONTENT_ITEM_TYPE)) {
            if (getValue(cursor, Event.TYPE, columnMemo).equals(Event.TYPE_BIRTHDAY)) {
                contact.addProperty("birthday", getValue(cursor, Event.START_DATE, columnMemo));
            }
        } else if (mimeType.equals(StructuredName.CONTENT_ITEM_TYPE)) {
            JsonObject name = new JsonObject();
            
            addIfPresent(name, "familyName", cursor, columnMemo,
                         StructuredName.FAMILY_NAME);
            addIfPresent(name, "formatted", cursor, columnMemo,
                         StructuredName.DISPLAY_NAME);
            addIfPresent(name, "givenName", cursor, columnMemo,
                         StructuredName.GIVEN_NAME);
            addIfPresent(name, "honorificPrefix", cursor, columnMemo,
                         StructuredName.PREFIX);
            addIfPresent(name, "honorificSuffix", cursor, columnMemo,
                         StructuredName.SUFFIX);
            addIfPresent(name, "middleName", cursor, columnMemo,
                         StructuredName.MIDDLE_NAME);
            contact.add("name", name);
        } else if (mimeType.equals(Email.CONTENT_ITEM_TYPE)) {
            JsonObject email = new JsonObject();
            JsonArray emails;

            email.addProperty("value",
                              getValue(cursor, Email.DATA1, columnMemo));
            email.addProperty("pref", false);
            switch (getValueOrMinusOne(cursor, Email.TYPE, columnMemo)) {
            case Email.TYPE_HOME:
                email.addProperty("type", "home");
                break;
            case Email.TYPE_WORK:
                email.addProperty("type", "work");
                break;
            case Email.TYPE_OTHER:
                email.addProperty("type", "other");
                break;
            case Email.TYPE_MOBILE:
                email.addProperty("type", "mobile");
                break;
            case BaseTypes.TYPE_CUSTOM:
                addIfPresent(email, "type", cursor, columnMemo,
                             Email.LABEL);
                break;
            default:
                email.add("type", JsonNull.INSTANCE);
                break;
            }
            if (contact.has("emails")) {
                emails = contact.getAsJsonArray("emails");
            } else {
                emails = new JsonArray();
            }
            emails.add(email);
            contact.add("emails", emails);
        } else if (mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
            JsonObject phone = new JsonObject();
            JsonArray phones;

            addIfPresent(phone, "value", cursor, columnMemo,
                         Phone.NUMBER);
            phone.addProperty("pref", false);
            switch (getValueOrMinusOne(cursor, Phone.TYPE, columnMemo)) {
            case Phone.TYPE_HOME:
                phone.addProperty("type", "home");
                break;
            case Phone.TYPE_MOBILE:
                phone.addProperty("type", "mobile");
                break;
            case Phone.TYPE_WORK:
                phone.addProperty("type", "work");
                break;
            case Phone.TYPE_FAX_WORK:
                phone.addProperty("type", "fax_work");
                break;
            case Phone.TYPE_FAX_HOME:
                phone.addProperty("type", "fax_home");
                break;
            case Phone.TYPE_PAGER:
                phone.addProperty("type", "pager");
                break;
            case Phone.TYPE_OTHER:
                phone.addProperty("type", "other");
                break;
            case Phone.TYPE_CALLBACK:
                phone.addProperty("type", "callback");
                break;
            case Phone.TYPE_CAR:
                phone.addProperty("type", "car");
                break;
            case Phone.TYPE_COMPANY_MAIN:
                phone.addProperty("type", "company_main");
                break;
            case Phone.TYPE_ISDN:
                phone.addProperty("type", "isdn");
                break;
            case Phone.TYPE_MAIN:
                phone.addProperty("type", "main");
                break;
            case Phone.TYPE_OTHER_FAX:
                phone.addProperty("type", "other_fax");
                break;
            case Phone.TYPE_RADIO:
                phone.addProperty("type", "radio");
                break;
            case Phone.TYPE_TELEX:
                phone.addProperty("type", "telex");
                break;
            case Phone.TYPE_TTY_TDD:
                phone.addProperty("type", "tty_tdd");
                break;
            case Phone.TYPE_WORK_MOBILE:
                phone.addProperty("type", "work_mobile");
                break;
            case Phone.TYPE_WORK_PAGER:
                phone.addProperty("type", "work_pager");
                break;
            case Phone.TYPE_ASSISTANT:
                phone.addProperty("type", "assistant");
                break;
            case Phone.TYPE_MMS:
                phone.addProperty("type", "mms");
                break;
            case BaseTypes.TYPE_CUSTOM:
                addIfPresent(phone, "type", cursor, columnMemo,
                             Phone.LABEL);
                break;
            default:
                phone.add("type", JsonNull.INSTANCE);
                break;
            }
            if (contact.has("phoneNumbers")) {
                phones = contact.getAsJsonArray("phoneNumbers");
            } else {
                phones = new JsonArray();
            }
            phones.add(phone);
            contact.add("phoneNumbers", phones);
        } else if (mimeType.equals(StructuredPostal.CONTENT_ITEM_TYPE)) {
            JsonObject address = new JsonObject();
            JsonArray addresses;
            addIfPresent(address, "formatted", cursor, columnMemo,
                         StructuredPostal.FORMATTED_ADDRESS);
            address.addProperty("pref", false);
			
            switch (getValueOrMinusOne(cursor, StructuredPostal.TYPE, columnMemo)) {
            case StructuredPostal.TYPE_HOME:
                address.addProperty("type", "home");
                break;
            case StructuredPostal.TYPE_WORK:
                address.addProperty("type", "work");
                break;
            case StructuredPostal.TYPE_OTHER:
                address.addProperty("type", "other");
                break;
            case BaseTypes.TYPE_CUSTOM:
                addIfPresent(address, "type", cursor, columnMemo,
                             StructuredPostal.LABEL);
                break;
            default:
                address.add("type", JsonNull.INSTANCE);
                break;
            }
			
            addIfPresent(address, "country", cursor, columnMemo,
                         StructuredPostal.COUNTRY);
            addIfPresent(address, "locality", cursor, columnMemo,
                         StructuredPostal.CITY);
            addIfPresent(address, "postalCode", cursor, columnMemo,
                         StructuredPostal.POSTCODE);
            addIfPresent(address, "region", cursor, columnMemo,
                         StructuredPostal.REGION);
            addIfPresent(address, "streetAddress", cursor, columnMemo,
                         StructuredPostal.STREET);
			
            if (contact.has("addresses")) {
                addresses = contact.getAsJsonArray("addresses");
            } else {
                addresses = new JsonArray();
            }
            addresses.add(address);
            contact.add("addresses", addresses);
        } else if (mimeType.equals(Im.CONTENT_ITEM_TYPE)) {
            JsonObject im = new JsonObject();
            JsonArray ims;
            addIfPresent(im, "value", cursor, columnMemo,
                         Im.DATA);
            im.addProperty("pref", false);
			
            switch (getValueOrMinusOne(cursor, Im.PROTOCOL, columnMemo)) {
            case Im.PROTOCOL_AIM:
                im.addProperty("type", "aim");
                break;
            case Im.PROTOCOL_MSN:
                im.addProperty("type", "msn");
                break;
            case Im.PROTOCOL_YAHOO:
                im.addProperty("type", "yahoo");
                break;
            case Im.PROTOCOL_SKYPE:
                im.addProperty("type", "skype");
                break;
            case Im.PROTOCOL_QQ:
                im.addProperty("type", "qq");
                break;
            case Im.PROTOCOL_GOOGLE_TALK:
                im.addProperty("type", "google_talk");
                break;
            case Im.PROTOCOL_ICQ:
                im.addProperty("type", "icq");
                break;
            case Im.PROTOCOL_JABBER:
                im.addProperty("type", "jabber");
                break;
            case Im.PROTOCOL_NETMEETING:
                im.addProperty("type", "netmeeting");
                break;
            case Im.PROTOCOL_CUSTOM:
                addIfPresent(im, "type", cursor, columnMemo,
                             Im.CUSTOM_PROTOCOL);
                break;
            default:
                im.add("type", JsonNull.INSTANCE);
                break;
            }
			
            if (contact.has("ims")) {
                ims = contact.getAsJsonArray("ims");
            } else {
                ims = new JsonArray();
            }
            ims.add(im);
            contact.add("ims", ims);
        } else if (mimeType.equals(Website.CONTENT_ITEM_TYPE)) {
            JsonObject url = new JsonObject();
            JsonArray urls;

            addIfPresent(url, "value", cursor, columnMemo,
                         Website.URL);
            url.addProperty("pref", false);
            switch (getValueOrMinusOne(cursor, Website.TYPE, columnMemo)) {
            case Website.TYPE_HOME:
                url.addProperty("type", "home");
                break;
            case Website.TYPE_HOMEPAGE:
                url.addProperty("type", "homepage");
                break;
            case Website.TYPE_BLOG:
                url.addProperty("type", "blog");
                break;
            case Website.TYPE_PROFILE:
                url.addProperty("type", "profile");
                break;
            case Website.TYPE_WORK:
                url.addProperty("type", "work");
                break;
            case Website.TYPE_FTP:
                url.addProperty("type", "ftp");
                break;
            case Website.TYPE_OTHER:
                url.addProperty("type", "other");
                break;
            case BaseTypes.TYPE_CUSTOM:
                addIfPresent(url, "type", cursor, columnMemo,
                             Website.LABEL);
                break;
            default:
                url.add("type", JsonNull.INSTANCE);
                break;
            }
			
            if (contact.has("urls")) {
                urls = contact.getAsJsonArray("urls");
            } else {
                urls = new JsonArray();
            }
            urls.add(url);
            contact.add("urls", urls);
        } else if (mimeType.equals(Organization.CONTENT_ITEM_TYPE)) {
            JsonObject organization = new JsonObject();
            JsonArray organizations;

            addIfPresent(organization, "name", cursor, columnMemo,
                         Organization.COMPANY);
            addIfPresent(organization, "department", cursor, columnMemo,
                         Organization.DEPARTMENT);
            addIfPresent(organization, "title", cursor, columnMemo,
                         Organization.TITLE);
            organization.addProperty("pref", false);
			
            switch (getValueOrMinusOne(cursor, Organization.TYPE, columnMemo)) {
            case Organization.TYPE_WORK:
                organization.addProperty("type", "work");
                break;
            case Organization.TYPE_OTHER:
                organization.addProperty("type", "other");
                break;
            case BaseTypes.TYPE_CUSTOM:
                addIfPresent(organization, "type", cursor, columnMemo,
                             Organization.LABEL);
                break;
            default:
                organization.add("type", JsonNull.INSTANCE);
                break;
            }
			
            if (contact.has("organizations")) {
                organizations = contact.getAsJsonArray("organizations");
            } else {
                organizations = new JsonArray();
            }
            organizations.add(organization);
            contact.add("organizations", organizations);
        } else if (mimeType.equals(Photo.CONTENT_ITEM_TYPE)) {
            JsonArray photos;
            if (contact.has("photos")) {
                photos = contact.getAsJsonArray("photos");
            } else {
                photos = new JsonArray();
            }
            try {
                JsonObject photo = new JsonObject();
                byte[] photoData = cursor.getBlob(cursor.getColumnIndexOrThrow(Photo.PHOTO));
                if (photoData != null) {
                    photo.addProperty("value", "data:image/jpg;base64," + Base64.encodeToString(photoData, Base64.NO_WRAP));
                    photo.addProperty("pref", false);
                }
                photos.add(photo);
            } catch (Exception e) {
            }
            contact.add("photos", photos);
        }
        return contact;
    }

    private static void 
    jsonConvertName(InsertOperation op, JsonObject obj) {
        op.checkField("familyName", StructuredName.FAMILY_NAME, obj);
        op.checkField("givenName", StructuredName.GIVEN_NAME, obj);
        op.checkField("middleName", StructuredName.MIDDLE_NAME, obj);
        op.checkField("honorificPrefix", StructuredName.PREFIX, obj);
        op.checkField("honorificSuffix", StructuredName.SUFFIX, obj);
    }

    private static void 
    jsonConvertDisplayName(InsertOperation op, JsonObject obj) {
        op.checkField("displayName", StructuredName.DISPLAY_NAME, obj);
    }

    private static void 
    jsonConvertBirthday(ArrayList<ContentProviderOperation> ops,
                        int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Event.CONTENT_ITEM_TYPE);

        op.checkField("birthday", Event.START_DATE, obj);
        op.withValue(Event.TYPE, Event.TYPE_BIRTHDAY);
        
        op.done(ops);
    }

    private static void 
    jsonConvertNote(ArrayList<ContentProviderOperation> ops,
                    int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Note.CONTENT_ITEM_TYPE);
        op.checkField("note", Note.NOTE, obj);

        op.done(ops);
    }

    private static void
    jsonConvertOrganization(ArrayList<ContentProviderOperation> ops,
                            int backRef, JsonObject obj) {
        // OK.  Allocate an op that looks at obj, which is to say the first 
        // organization entry...

        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Organization.CONTENT_ITEM_TYPE);

        op.checkField("name", Organization.COMPANY);
        op.checkField("department", Organization.DEPARTMENT);
        op.checkField("title", Organization.TITLE);
        op.mapType(typeMapOrganization,
                   Organization.TYPE, Organization.TYPE_CUSTOM,
                   Organization.LABEL);

        op.done(ops);
    }

    private static void
    jsonConvertPhone(ArrayList<ContentProviderOperation> ops,
                     int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Phone.CONTENT_ITEM_TYPE);

        op.checkField("value", Phone.NUMBER);
        op.mapType(typeMapPhone,
                   Phone.TYPE, Phone.TYPE_CUSTOM,
                   Phone.LABEL);

        op.done(ops);
    }

    private static void
    jsonConvertEmail(ArrayList<ContentProviderOperation> ops,
                     int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Email.CONTENT_ITEM_TYPE);

        op.checkField("value", Email.ADDRESS);
        op.mapType(typeMapEmail,
                   Email.TYPE, Email.TYPE_CUSTOM,
                   Email.LABEL);

        op.done(ops);
    }

    private static void
    jsonConvertIM(ArrayList<ContentProviderOperation> ops,
                     int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Im.CONTENT_ITEM_TYPE);

        op.checkField("value", Im.DATA);

        // "type" in the IM W3C entry really means "protocol".  There isn't
        // a separate type, so we ignore Im.TYPE.

        op.mapType(typeMapIMProtocol,
                   Im.PROTOCOL, Im.PROTOCOL_CUSTOM,
                   Im.CUSTOM_PROTOCOL);

        op.done(ops);
    }

    private static void
    jsonConvertURL(ArrayList<ContentProviderOperation> ops,
                     int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Website.CONTENT_ITEM_TYPE);

        op.checkField("value", Website.URL);
        op.mapType(typeMapURL,
                   Website.TYPE, Website.TYPE_CUSTOM,
                   Website.LABEL);

        op.done(ops);
    }

    private static void
    jsonConvertAddress(ArrayList<ContentProviderOperation> ops,
                     int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                StructuredPostal.CONTENT_ITEM_TYPE);

        op.checkField("formatted", StructuredPostal.FORMATTED_ADDRESS);
        op.checkField("streetAddress", StructuredPostal.STREET);
        // We don't support POBOX or NEIGHBORHOOD.
        op.checkField("locality", StructuredPostal.CITY);
        op.checkField("region", StructuredPostal.REGION);
        op.checkField("postalCode", StructuredPostal.POSTCODE);
        op.checkField("country", StructuredPostal.COUNTRY);

        op.mapType(typeMapAddress,
                   StructuredPostal.TYPE, StructuredPostal.TYPE_CUSTOM,
                   StructuredPostal.LABEL);

        op.done(ops);
    }

    private static void iterateElements(ArrayList<ContentProviderOperation> ops,
                                        int backRef, String what, 
                                        JsonElement element, int maxCount,
                                        JSONConverter conv) {
        if (!element.isJsonArray()) {
            // Not us.
            ForgeLog.d("- " + what + ": not a JSON array");
            return;
        }

        JsonArray elementArray = element.getAsJsonArray();

        if (elementArray.size() == 0) {
            ForgeLog.d("- " + what + ": empty array");
            return;
        }
        
        int count = 0;

        for (JsonElement subElement : elementArray) {
            if (!subElement.isJsonObject()) {
                // Hmm.  Weird, man.
                ForgeLog.e("- " + what +
                           ": element " + count + " is not an object");
                continue;
            }

            conv.execute(ops, backRef, subElement.getAsJsonObject());

            count++;

            if ((maxCount > 0) && (count >= maxCount)) {
                break;
            }
        }
    }

    public static ArrayList<ContentProviderOperation>
    opsFromJSONObject(ForgeTask task, String accountType, String accountName,
                      JsonObject contact) {
        // Start by allocating our operation array...

        ArrayList<ContentProviderOperation> ops =
            new ArrayList<ContentProviderOperation>();

        // ...getting the backreference set up (I know, it's always zero
        // in this method, but this is more likely to be refactor-safe)...

        int rawContactInsertIndex = ops.size();

        // ...and setting up the initial insertion operation.

        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .build());

        // After that, we have to walk fields and wrangle stuff type by type.
        // Thanks, Google -- WTF were you thinking?
        //
        // Just to make it worse, we have to screw around with displayName
        // and the other name information appear separately in JSON, but in
        // the same element in the Android world.  Sigh.

        InsertOperation nameOp = 
            new InsertOperation(contact, rawContactInsertIndex, 
                                StructuredName.CONTENT_ITEM_TYPE);

        ForgeLog.i("working with contact " + contact);
        
        for (JsonElement jsonField : allFieldsForAdd) {
            String field = jsonField.getAsString();
            JsonElement child = contact.get(field);

//            ForgeLog.d("checking for " + field + ": has " +
//                       (contact.has(field) ? "Y" : "N") + 
//                       ", by content "  + 
//                       ((child == null) ? "N" : "Y"));

            if (child == null) {
                continue;
            }

            ForgeLog.d("found field " + field);
            JSONConverter conv = null;
            int maxCount = 0;

            if (field.equals("name")) {
                jsonConvertName(nameOp, child.getAsJsonObject());
            }
            else if (field.equals("displayName")) {
                // Note that we pass contact here, not child, because 
                // displayName is a scalar, not an object.
                ForgeLog.d("calling ConvertDisplayName");
                jsonConvertDisplayName(nameOp, contact);
            }
            else if (field.equals("birthday")) {
                // Note that we pass contact here, not child, because 
                // birthday is a scalar, not an object.
                ForgeLog.d("calling ConvertBirthday");
                jsonConvertBirthday(ops, rawContactInsertIndex, contact);
            }
            else if (field.equals("note")) {
                // Note that we pass contact here, not child, because 
                // note is a scalar, not an object.
                ForgeLog.d("calling ConvertNote");
                jsonConvertNote(ops, rawContactInsertIndex, contact);
            }
            else if (field.equals("organizations")) {
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertOrganization(ops, backRef, obj);
                    }
                };

                maxCount = 1;
            }
            else if (field.equals("phoneNumbers")) {
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertPhone(ops, backRef, obj);
                    }
                };
            }
            else if (field.equals("addresses")) {
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertAddress(ops, backRef, obj);
                    }
                };
            }
            else if (field.equals("emails")) {
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertEmail(ops, backRef, obj);
                    }
                };
            }
            else if (field.equals("ims")) {
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertIM(ops, backRef, obj);
                    }
                };
            }
            else if (field.equals("urls")) {
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertURL(ops, backRef, obj);
                    }
                };
            }

            if (conv != null) {
                ForgeLog.d("- iterating for " + field);
                iterateElements(ops, rawContactInsertIndex, field,
                				child, maxCount, conv);
            }
        }

        // Close out the name op.
        nameOp.done(ops);

        return ops;
    }
}
