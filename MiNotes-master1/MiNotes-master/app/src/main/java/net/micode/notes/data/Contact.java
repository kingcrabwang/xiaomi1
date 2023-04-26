package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

public class Contact {

    // 声明一个用于缓存联系人的HashMap
    private static HashMap<String, String> sContactCache;

    // 声明日志输出的TAG
    private static final String TAG = "Contact";

    // 定义查询联系人的选择条件，使用占位符 "?" 和 "IN" 子句
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 获取联系人名称
     * @param context 上下文环境
     * @param phoneNumber 联系人的电话号码
     * @return 联系人的名称，如果找不到返回null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 如果联系人缓存为空，则创建一个新的HashMap
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 如果联系人缓存中已经存在该电话号码的联系人，则直接返回联系人名称
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        // 使用PhoneNumberUtils工具类将电话号码转换为拨号最小匹配
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 查询联系人
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,
                new String [] { Phone.DISPLAY_NAME },
                selection,
                new String[] { phoneNumber },
                null);

        // 如果查询到联系人，则将联系人名称存入缓存并返回联系人名称
        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);
                sContactCache.put(phoneNumber, name);
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();
            }
        } else {
            // 如果找不到联系人，则打印日志并返回null
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}