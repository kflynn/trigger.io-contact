package io.trigger.forge.android.modules.contact;

import io.trigger.forge.android.core.ForgeApp;
import io.trigger.forge.android.core.ForgeLog;
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

/**
 * When adding contacts, we need to convert from W3C Contact objects into
 * Android's special craziness.  The trivial ways to do that in Java all
 * make for a huge amount of repetitive typing; to cut down on that, we
 * use the JSONConverter interface to set up indirect calls to the various
 * converters.
 *
 * All the converters work by, basically, creating an InsertOperation and
 * just ripping down the relevant properties, using the InsertOperation's
 * checkProperty and mapType methods.  At the end, they use the
 * InsertOperation's done method to wrap things up.
 */

interface JSONConverter {
    /** 
     * Execute a conversion.  If all goes well, a single new
     * ContentProviderOperation is added to ops; if not, nothing is added.
     *
     * @param ops     Array of insertion operations to add our contact
     * @param backRef The index of the raw contact insertion in ops
     * @param obj     The JsonObject we'll be converting
     */

    public void execute(ArrayList<ContentProviderOperation> ops,
                        int backRef, JsonObject obj);
}

/**
 * InsertOperation is basically a ContentProviderOperation.Builder, but 
 * it keeps track of the JsonObject it's meant to be referencing, and it
 * keeps track of whether that object actually had any fields to be added.
 */

class InsertOperation {
    private JsonObject obj;
    private ContentProviderOperation.Builder op;
    private boolean needsAdd;

    /**
     * Base constructor.
     *
     * @param obj          The JsonObject we're going to be studying
     * @param backRef      Index of the raw-contact operation we're
     *                     associated with
     * @param contentType  MIME type for this insert operation.
     */

    public InsertOperation(JsonObject obj, int backRef, String contentType) {
        // Save the object we'll be looking at...
        this.obj = obj;

        // ...gen up a new ContentProviderOperation.Builder using the
        // given backRef and contentType...

        this.op =  ContentProviderOperation.newInsert(Data.CONTENT_URI)
                   .withValueBackReference(Data.RAW_CONTACT_ID, backRef)
                   .withValue(Data.MIMETYPE, contentType);

        // ...and start out assuming that we _don't_ need to actually save
        // this.op.  (If we end up not needing this.op at all, the GC will
        // clean it up.)

        this.needsAdd = false;
    }

    /**
     * Fetch a property from our JsonObject.
     *
     * @param propName The name of the property to fetch
     * @return A String value.  Missing properties and properties with
     *         empty values will both be returned as null
     */

    private String fetch(String propName) {
        return fetch(propName, this.obj);
    }

    /**
     * Fetch a property from an arbitrary JsonObject.
     *
     * Why is this here, if the point of the InsertOperation is that it
     * saves the object you want to look at?  Mostly for name/displayName,
     * which are stored in _separate_ JsonObjects in the W3C model, but
     * must be combined into _the same_ ContentProviderOperation.
     *
     * @param propName The name of the property to fetch
     * @param obj The JsonObject to look into.
     * @return A String value.  Missing properties and properties with
     *         empty values will both be returned as null
     */

    private String fetch(String propName, JsonObject obj) {
        JsonElement child = obj.get(propName);

        if (child == null) {
            ForgeLog.d("- no property " + propName);
            return null;
        }

        String value = child.getAsString();

        if ((value == null) || (value.length() <= 0)) {
            ForgeLog.d("- empty value for " + propName);
            return null;
        }

        return value;
    }

    /**
     * Shortcut to this.op.withValue: add a key/value pair to our
     * ContentProviderOperation.
     *
     * @param key   Key to use when adding
     * @param value Value to add (a String)
     */

    public void withValue(String key, String value) {
        this.op.withValue(key, value);
    }

    /**
     * Shortcut to this.op.withValue: add a key/value pair to our
     * ContentProviderOperation.
     *
     * @param key   Key to use when adding
     * @param value Value to add (an int)
     */

    public void withValue(String key, int value) {
        this.op.withValue(key, value);
    }

    /**
     * If a given property exists on our JsonObject, add its value to our
     * ContentProviderOperation, and set this.needsAdd so that we know we
     * have some real data.
     *
     * @param propName Property name to check for
     * @param key      Key to add under, if it exists
     */

    public void checkProperty(String propName, String key) {
        checkProperty(propName, key, this.obj);
    }

    /**
     * If a given property exists on an arbitrary JsonObject, add its
     * value to our ContentProviderOperation, and set this.needsAdd so
     * that we know we have some real data.
     *
     * Why is this here, if the point of the InsertOperation is that it
     * saves the object you want to look at?  Mostly for name/displayName,
     * which are stored in _separate_ JsonObjects in the W3C model, but
     * must be combined into _the same_ ContentProviderOperation.
     *
     * @param propName Property name to check for
     * @param key      Key to add under, if it exists
     * @param obj      JsonObject to look into
     */

    public void checkProperty(String propName, String key, JsonObject obj) {
        // Grab the property in question...
        String value = this.fetch(propName, obj);

        // ...and bail if it's not anything relevant.
        if (value == null) {
            return;
        }

        // OK, it's for real.  Add it to our op...
        ForgeLog.d("- adding op for " + key + ": " + value);
        this.withValue(key, value);

        // ...and remember that we added things.
        this.needsAdd = true;
    } 

    /**
     * Map a String type name to an integer type ID, because Android
     * really really likes integer type IDs.
     *
     * DO NOT set this.needsAdd here.  If the only thing we have for this
     * object is a type, that's not actually real data.
     *
     * @param typeMap    HashMap mapping type names to type ID
     * @param typeKey    Key to use when saving type ID
     * @param customType Type ID for custom type
     * @param labelKey   Key to use for custom type
     */

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
            // We have a type ID -- save it using the typeKey.
            ForgeLog.d("-- type " + objType + ": " + mappedType);
            this.op.withValue(typeKey, mappedType);
        }
        else {
            // We have no type ID.  Instead, save the customType under
            // the typeKey, and save the actual unmappable type name under
            // the labelKey.

            ForgeLog.d("-- custom type " + objType + ": " + 
                       customType + ", setting " + labelKey);
            this.op.withValue(typeKey, customType)
                   .withValue(labelKey, objType);
        }
    }

    /**
     * We're finished.  If we found anything, build out our
     * ContentProviderOperation and add it to the given
     * ContentProviderOperation list.
     *
     * @param ops ContentProviderOperation list to add our op to
     */

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

        // typeMap* map W3C type strings to Android type IDs.
        //
        // XXX
        // When reading, there're a bunch of switch()es that perform
        // the reverse mapping.  We should make the typeMaps objects
        // that can map either direction -- they're always 1-to-1
        // mappings, after all.

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

    /**
     * Convert a W3C name into an Android op.
     *
     * NOTE WELL: THIS METHOD IS NOT CALLED THROUGH A JSONConverter.  It
     * does NOT have the same call signature as one of the methods that
     * is.
     *
     * Why not?  name and displayName are separated in the W3C model, but
     * combined in the Android model.  As such jsonConvertName and 
     * jsonConvertDisplayName get called directly, and are passed an
     * InsertOperation rather than creating one.
     *
     * @param op   InsertOperation to use during conversion
     * @param obj  The JsonObject we'll be converting
     */

    private static void 
    jsonConvertName(InsertOperation op, JsonObject obj) {
        op.checkProperty("familyName", StructuredName.FAMILY_NAME, obj);
        op.checkProperty("givenName", StructuredName.GIVEN_NAME, obj);
        op.checkProperty("middleName", StructuredName.MIDDLE_NAME, obj);
        op.checkProperty("honorificPrefix", StructuredName.PREFIX, obj);
        op.checkProperty("honorificSuffix", StructuredName.SUFFIX, obj);
    }

    /**
     * Convert a W3C displayName into an Android op.
     *
     * NOTE WELL: THIS METHOD IS NOT CALLED THROUGH A JSONConverter.  It
     * does NOT have the same call signature as one of the methods that
     * is.
     *
     * Why not?  name and displayName are separated in the W3C model, but
     * combined in the Android model.  As such jsonConvertName and 
     * jsonConvertDisplayName get called directly, and are passed an
     * InsertOperation rather than creating one.
     *
     * @param op   InsertOperation to use during conversion
     * @param obj  The JsonObject we'll be converting
     */

    private static void 
    jsonConvertDisplayName(InsertOperation op, JsonObject obj) {
        op.checkProperty("displayName", StructuredName.DISPLAY_NAME, obj);
    }

    /**
     * Convert a W3C birthday into an Android op.  
     *
     * Though this conforms to the JSONConverter calling convention, and
     * you can refer to the JSONConverter documentation for the basics of
     * what's going on here, it is not actually called through a
     * JSONConverter, since birthday is a scalar rather than an array of
     * JsonObjects. 
     *
     * @param ops      ContentProviderOperation list to add our new ops to
     * @param backRef  Index of the raw-contact operation we're
     *                 associated with
     * @param obj      The JsonObject we'll be converting
     */

    private static void 
    jsonConvertBirthday(ArrayList<ContentProviderOperation> ops,
                        int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Event.CONTENT_ITEM_TYPE);

        op.checkProperty("birthday", Event.START_DATE, obj);
        op.withValue(Event.TYPE, Event.TYPE_BIRTHDAY);
        
        op.done(ops);
    }

    /**
     * Convert a W3C note into an Android op.  
     *
     * Though this conforms to the JSONConverter calling convention, and
     * you can refer to the JSONConverter documentation for the basics of
     * what's going on here, it is not actually called through a
     * JSONConverter, since note is a scalar rather than an array of
     * JsonObjects.
     *
     * @param ops      ContentProviderOperation list to add our new ops to
     * @param backRef  Index of the raw-contact operation we're
     *                 associated with
     * @param obj      The JsonObject we'll be converting
     */

    private static void 
    jsonConvertNote(ArrayList<ContentProviderOperation> ops,
                    int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Note.CONTENT_ITEM_TYPE);
        op.checkProperty("note", Note.NOTE, obj);

        op.done(ops);
    }

    /**
     * Convert a W3C organization into an Android op.  See the
     * JSONConverter documentation for the basics of what's going on here.
     *
     * @param ops      ContentProviderOperation list to add our new ops to
     * @param backRef  Index of the raw-contact operation we're
     *                 associated with
     * @param obj      The JsonObject we'll be converting
     */

    private static void
    jsonConvertOrganization(ArrayList<ContentProviderOperation> ops,
                            int backRef, JsonObject obj) {
        // OK.  Allocate an op that looks at obj, which is to say the first 
        // organization entry...

        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Organization.CONTENT_ITEM_TYPE);

        op.checkProperty("name", Organization.COMPANY);
        op.checkProperty("department", Organization.DEPARTMENT);
        op.checkProperty("title", Organization.TITLE);
        op.mapType(typeMapOrganization,
                   Organization.TYPE, Organization.TYPE_CUSTOM,
                   Organization.LABEL);

        op.done(ops);
    }

    /**
     * Convert a W3C phone number into an Android op.  See the
     * JSONConverter documentation for the basics of what's going on here.
     *
     * @param ops      ContentProviderOperation list to add our new ops to
     * @param backRef  Index of the raw-contact operation we're
     *                 associated with
     * @param obj      The JsonObject we'll be converting
     */

    private static void
    jsonConvertPhone(ArrayList<ContentProviderOperation> ops,
                     int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Phone.CONTENT_ITEM_TYPE);

        op.checkProperty("value", Phone.NUMBER);
        op.mapType(typeMapPhone,
                   Phone.TYPE, Phone.TYPE_CUSTOM,
                   Phone.LABEL);

        op.done(ops);
    }

    /**
     * Convert a W3C email address into an Android op.  See the
     * JSONConverter documentation for the basics of what's going on here.
     *
     * @param ops      ContentProviderOperation list to add our new ops to
     * @param backRef  Index of the raw-contact operation we're
     *                 associated with
     * @param obj      The JsonObject we'll be converting
     */

    private static void
    jsonConvertEmail(ArrayList<ContentProviderOperation> ops,
                     int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Email.CONTENT_ITEM_TYPE);

        // Use Email.DATA instead of Email.ADDRESS for 
        // Android 2 compatibility.
        
        op.checkProperty("value", Email.DATA);
        op.mapType(typeMapEmail,
                   Email.TYPE, Email.TYPE_CUSTOM,
                   Email.LABEL);

        op.done(ops);
    }

    /**
     * Convert a W3C address into an Android op.  See the JSONConverter
     * documentation for the basics of what's going on here.
     *
     * @param ops       ContentProviderOperation list to add our new ops to
     * @param backRef   Index of the raw-contact operation we're
     *                  associated with
     * @param obj     The JsonObject we'll be converting
     */

    private static void
    jsonConvertIM(ArrayList<ContentProviderOperation> ops,
                  int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Im.CONTENT_ITEM_TYPE);

        op.checkProperty("value", Im.DATA);

        // "type" in the IM W3C entry really means "protocol".  There isn't
        // a separate type, so we ignore Im.TYPE.

        op.mapType(typeMapIMProtocol,
                   Im.PROTOCOL, Im.PROTOCOL_CUSTOM,
                   Im.CUSTOM_PROTOCOL);

        op.done(ops);
    }

    /**
     * Convert a W3C website into an Android op.  See the JSONConverter
     * documentation for the basics of what's going on here.
     *
     * @param ops      ContentProviderOperation list to add our new ops to
     * @param backRef  Index of the raw-contact operation we're
     *                 associated with
     * @param obj      The JsonObject we'll be converting
     */

    private static void
    jsonConvertURL(ArrayList<ContentProviderOperation> ops,
                   int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                Website.CONTENT_ITEM_TYPE);

        op.checkProperty("value", Website.URL);
        op.mapType(typeMapURL,
                   Website.TYPE, Website.TYPE_CUSTOM,
                   Website.LABEL);

        op.done(ops);
    }

    /**
     * Convert a W3C address into an Android op.  See the JSONConverter
     * documentation for the basics of what's going on here.
     *
     * @param ops      ContentProviderOperation list to add our new ops to
     * @param backRef  Index of the raw-contact operation we're
     *                 associated with
     * @param obj      The JsonObject we'll be converting
     */

    private static void
    jsonConvertAddress(ArrayList<ContentProviderOperation> ops,
                       int backRef, JsonObject obj) {
        InsertOperation op = 
            new InsertOperation(obj, backRef, 
                                StructuredPostal.CONTENT_ITEM_TYPE);

        op.checkProperty("formatted", StructuredPostal.FORMATTED_ADDRESS);
        op.checkProperty("streetAddress", StructuredPostal.STREET);
        // We don't support POBOX or NEIGHBORHOOD.
        op.checkProperty("locality", StructuredPostal.CITY);
        op.checkProperty("region", StructuredPostal.REGION);
        op.checkProperty("postalCode", StructuredPostal.POSTCODE);
        op.checkProperty("country", StructuredPostal.COUNTRY);

        op.mapType(typeMapAddress,
                   StructuredPostal.TYPE, StructuredPostal.TYPE_CUSTOM,
                   StructuredPostal.LABEL);

        op.done(ops);
    }

    /**
     * Iterate over all the objects contained in a JsonArray and call a
     * converter method for each of them.
     *
     * @param ops       ContentProviderOperation list to add our new ops to
     * @param backRef   Index of the raw-contact operation we're
     *                  associated with
     * @param what      Human-readable name for the kind of thing we're
     *                  iterating (used for debugging)
     * @param element   JsonElement to iterate (must be a JsonArray
     *                  underneath) 
     * @param maxCount  Maximum number of iterations (0 means no limit)
     * @param conv      JSONConverter to call for each contained object
     */

    private static void iterateElements(ArrayList<ContentProviderOperation> ops,
                                        int backRef, String what, 
                                        JsonElement element, int maxCount,
                                        JSONConverter conv) {
        // Make sure we have a JsonArray...

        if (!element.isJsonArray()) {
            // Not us.
            ForgeLog.d("- " + what + ": not a JSON array");
            return;
        }

        JsonArray elementArray = element.getAsJsonArray();

        // ...that contains some data.

        if (elementArray.size() == 0) {
            ForgeLog.d("- " + what + ": empty array");
            return;
        }
        
        // OK.  Remember how many we've done, and off we go.

        int count = 0;

        for (JsonElement subElement : elementArray) {
            if (!subElement.isJsonObject()) {
                // This shouldn't ever happen the way we're using this. 
                // Continue, I guess.

                ForgeLog.e("- " + what +
                           ": element " + count + " is not an object");
                continue;
            }

            // Run the converter...
            conv.execute(ops, backRef, subElement.getAsJsonObject());

            // ...and figure out if we need to be done.
            count++;

            if ((maxCount > 0) && (count >= maxCount)) {
                break;
            }
        }
    }

    /**
     * Convert a W3C Contact into a list of ContentProviderOperations
     * that will add it to the Android contact list.
     *
     * @param accountType  Account type under which to add contact
     * @param accountName  Account name under which to add contact
     * @param contact      W3C Contact object representing contact to add
     * @return An ArrayList of ContentProviderOperation, suitable for 
     *         handing off to a ContentResolver's applyBatch method
     */

    public static ArrayList<ContentProviderOperation>
    opsFromJSONObject(String accountType, String accountName,
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
        // Just to make it worse, displayName and the other name
        // information appear separately in JSON, but in the same element
        // in the Android world.  Sigh.  We'll wrangle that by creating
        // our nameOp up front.

        InsertOperation nameOp = 
            new InsertOperation(contact, rawContactInsertIndex, 
                                StructuredName.CONTENT_ITEM_TYPE);

        ForgeLog.d("working with contact " + contact);
        
        for (JsonElement jsonField : allFieldsForAdd) {
            // Grab the property name from our jsonField, and see if
            // there's really a property there.
            String propName = jsonField.getAsString();
            JsonElement property = contact.get(propName);

            if (property == null) {
                continue;
            }

            ForgeLog.d("found property " + propName);

            // Start by assuming that we don't need to convert this
            // property, but that if we change our mind later, we'll want
            // to convert all instances contained in the property.

            JSONConverter conv = null;
            int maxCount = 0;

            if (propName.equals("name")) {
                // We could use iterateElements for this, but we
                // already have nameOp, so just call jsonConvertName
                // directly.

                jsonConvertName(nameOp, property.getAsJsonObject());
            }
            else if (propName.equals("displayName")) {
                // We need to call jsonConvertDisplayName directly here
                // (and pass contact, not property) because displayName is
                // a scalar, not an object.
                ForgeLog.d("calling ConvertDisplayName");
                jsonConvertDisplayName(nameOp, contact);
            }
            else if (propName.equals("birthday")) {
                // We need to call jsonConvertBirthday directly here (and
                // pass contact, not property) because birthday is a
                // scalar, not an object.
                ForgeLog.d("calling ConvertBirthday");
                jsonConvertBirthday(ops, rawContactInsertIndex, contact);
            }
            else if (propName.equals("note")) {
                // We need to call jsonConvertNote directly here (and pass
                // contact, not property) because note is a scalar, not an
                // object.
                ForgeLog.d("calling ConvertNote");
                jsonConvertNote(ops, rawContactInsertIndex, contact);
            }
            else if (propName.equals("organizations")) {
                // Set conv for later iteration (but only do the first
                // organization, not all of them).

                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertOrganization(ops, backRef, obj);
                    }
                };

                // We only support one organization, so make sure we stop
                // iterating after the first one.
                maxCount = 1;
            }
            else if (propName.equals("phoneNumbers")) {
                // Set conv for later iteration.
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertPhone(ops, backRef, obj);
                    }
                };
            }
            else if (propName.equals("addresses")) {
                // Set conv for later iteration.
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertAddress(ops, backRef, obj);
                    }
                };
            }
            else if (propName.equals("emails")) {
                // Set conv for later iteration.
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertEmail(ops, backRef, obj);
                    }
                };
            }
            else if (propName.equals("ims")) {
                // Set conv for later iteration.
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertIM(ops, backRef, obj);
                    }
                };
            }
            else if (propName.equals("urls")) {
                // Set conv for later iteration.
                conv = new JSONConverter() {
                    public void execute(ArrayList<ContentProviderOperation> ops,
                                        int backRef, JsonObject obj) {
                        jsonConvertURL(ops, backRef, obj);
                    }
                };
            }

            // Do we need to iterate over this element?

            if (conv != null) {
                // Yup.  Let iterateElements do the heavy lifting here.
                ForgeLog.d("- iterating for " + propName);
                iterateElements(ops, rawContactInsertIndex, propName,
                                property, maxCount, conv);
            }
        }

        // Close out the name op.
        nameOp.done(ops);

        return ops;
    }
}
