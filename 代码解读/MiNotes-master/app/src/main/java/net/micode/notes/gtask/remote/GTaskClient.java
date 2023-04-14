/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.gtask.remote;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.gtask.data.Node;
import net.micode.notes.gtask.data.Task;
import net.micode.notes.gtask.data.TaskList;
import net.micode.notes.gtask.exception.ActionFailureException;
import net.micode.notes.gtask.exception.NetworkFailureException;
import net.micode.notes.tool.GTaskStringUtils;
import net.micode.notes.ui.NotesPreferenceActivity;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;


/**
 * GTaskClient类，用于管理Google任务。
 */
public class GTaskClient {
    private static final String TAG = GTaskClient.class.getSimpleName();

    // Google任务的URL地址
    private static final String GTASK_URL = "https://mail.google.com/tasks/";

    // 获取任务的URL地址
    private static final String GTASK_GET_URL = "https://mail.google.com/tasks/ig";

    // 提交任务的URL地址
    private static final String GTASK_POST_URL = "https://mail.google.com/tasks/r/ig";

    // GTaskClient实例，使用单例模式
    private static GTaskClient mInstance = null;

    // HTTP客户端
    private DefaultHttpClient mHttpClient;

    // 获取任务的URL地址
    private String mGetUrl;

    // 提交任务的URL地址
    private String mPostUrl;

    // 客户端版本号
    private long mClientVersion;

    // 是否已登录
    private boolean mLoggedin;

    // 上次登录时间
    private long mLastLoginTime;

    // 操作ID
    private int mActionId;

    // 用户账号
    private Account mAccount;

    // 更新的任务列表
    private JSONArray mUpdateArray;

    /**
     * 构造函数，初始化成员变量
     */
    private GTaskClient() {
        mHttpClient = null;
        mGetUrl = GTASK_GET_URL;
        mPostUrl = GTASK_POST_URL;
        mClientVersion = -1;
        mLoggedin = false;
        mLastLoginTime = 0;
        mActionId = 1;
        mAccount = null;
        mUpdateArray = null;
    }

    /**
     * 获取GTaskClient实例，使用单例模式
     */
    public static synchronized GTaskClient getInstance() {
        if (mInstance == null) {
            mInstance = new GTaskClient();
        }
        return mInstance;
    }

    /**
     * 登录Google账号，返回是否登录成功
     * @param activity 当前活动的Activity
     */
    public boolean login(Activity activity) {
        // 假设Cookie会在5分钟后过期，需要重新登录
        final long interval = 1000 * 60 * 5;
        if (mLastLoginTime + interval < System.currentTimeMillis()) {
            mLoggedin = false;
        }

        // 如果账号已切换，则需要重新登录
        if (mLoggedin
                && !TextUtils.equals(getSyncAccount().name, NotesPreferenceActivity
                .getSyncAccountName(activity))) {
            mLoggedin = false;
        }

        if (mLoggedin) {
            Log.d(TAG, "已经登录");
            return true;
        }

        mLastLoginTime = System.currentTimeMillis();
        String authToken = loginGoogleAccount(activity, false);
        if (authToken == null) {
            Log.e(TAG, "登录Google账号失败");
            return false;
        }

        // 如果是自定义域名，则使用特定URL登录
        if (!(mAccount.name.toLowerCase().endsWith("gmail.com") || mAccount.name.toLowerCase()
                .endsWith("googlemail.com"))) {
            StringBuilder url = new StringBuilder(GTASK_URL).append("a/");
            int index = mAccount.name.indexOf('@') + 1;
            String suffix = mAccount.name.substring(index);
            url.append(suffix + "/");
            mGetUrl = url.toString() + "ig";
            mPostUrl = url.toString() + "r/ig";

            if (tryToLoginGtask(activity, authToken)) {
                mLoggedin = true;
            }
        }

        // 尝试使用Google官方URL登录
        if (!mLoggedin) {
            mGetUrl = GTASK_GET_URL;
            mPostUrl = GTASK_POST_URL;
            if (!tryToLoginGtask(activity, authToken)) {
                return false;
            }
        }

        mLoggedin = true;
        return true;
    }


    /**
     * 通过 Google 账号登录获取 authToken
     * @param activity Activity 对象
     * @param invalidateToken 是否使 token 失效
     * @return authToken 字符串，成功则返回，否则返回 null
     */
    private String loginGoogleAccount(Activity activity, boolean invalidateToken) {
        String authToken;
        AccountManager accountManager = AccountManager.get(activity);
        Account[] accounts = accountManager.getAccountsByType("com.google");

        // 检查是否有可用的 Google 账号
        if (accounts.length == 0) {
            Log.e(TAG, "there is no available google account");
            return null;
        }

        // 从设置中获取同名账号
        String accountName = NotesPreferenceActivity.getSyncAccountName(activity);
        Account account = null;
        for (Account a : accounts) {
            if (a.name.equals(accountName)) {
                account = a;
                break;
            }
        }
        if (account != null) {
            mAccount = account;
        } else {
            Log.e(TAG, "unable to get an account with the same name in the settings");
            return null;
        }

        // 获取 token
        AccountManagerFuture<Bundle> accountManagerFuture = accountManager.getAuthToken(account,
                "goanna_mobile", null, activity, null, null);
        try {
            Bundle authTokenBundle = accountManagerFuture.getResult();
            authToken = authTokenBundle.getString(AccountManager.KEY_AUTHTOKEN);

            // 如果需要使 token 失效，则调用自身再次获取 token
            if (invalidateToken) {
                accountManager.invalidateAuthToken("com.google", authToken);
                loginGoogleAccount(activity, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "get auth token failed");
            authToken = null;
        }

        return authToken;
    }

    /**
     * 尝试登录 Gtask
     * @param activity Activity 对象
     * @param authToken 认证 token
     * @return 登录是否成功
     */
    private boolean tryToLoginGtask(Activity activity, String authToken) {
        if (!loginGtask(authToken)) {
            // 如果 token 失效，尝试重新获取 token 再次登录
            authToken = loginGoogleAccount(activity, true);
            if (authToken == null) {
                Log.e(TAG, "login google account failed");
                return false;
            }

            if (!loginGtask(authToken)) {
                Log.e(TAG, "login gtask failed");
                return false;
            }
        }
        return true;
    }

    /**
     * 登录 Gtask
     * @param authToken 认证 token
     * @return 登录是否成功
     */
    private boolean loginGtask(String authToken) {
        int timeoutConnection = 10000;
        int timeoutSocket = 15000;
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, timeoutConnection);
        HttpConnectionParams.setSoTimeout(httpParameters, timeoutSocket);
        mHttpClient = new DefaultHttpClient(httpParameters);
        BasicCookieStore localBasicCookieStore = new BasicCookieStore();
        mHttpClient.setCookieStore(localBasicCookieStore);
        HttpProtocolParams.setUseExpectContinue(mHttpClient.getParams(), false);

        // 登录 Gtask
        try {
            String loginUrl = mGetUrl + "?auth=" + authToken;
            HttpGet httpGet = new HttpGet(loginUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // 获取 cookie
            List<Cookie> cookies = mHttpClient.getCookieStore().getCookies();
            boolean hasAuthCookie = false;
            for (Cookie cookie : cookies) {
                if (cookie.getName().contains("GTL")) {
                    hasAuthCookie = true;
                }
            }
            if (!hasAuthCookie) {
                Log.w(TAG, "it seems that there is no auth cookie");
            }

            // 获取客户端版本号
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            mClientVersion = js.getLong("v");
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            // 捕获所有异常
            Log.e(TAG, "httpget gtask_url failed");
            return false;
        }

        return true;
    }

    // 获取操作ID
    private int getActionId() {
        return mActionId++;
    }

    // 创建HTTP POST请求
    private HttpPost createHttpPost() {
        // 创建HttpPost对象，传入POST请求的URL
        HttpPost httpPost = new HttpPost(mPostUrl);
        // 设置请求头信息
        httpPost.setHeader("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
        httpPost.setHeader("AT", "1");
        return httpPost;
    }

    // 获取响应内容
    private String getResponseContent(HttpEntity entity) throws IOException {
        // 初始化内容编码为null
        String contentEncoding = null;
        // 如果实体的内容编码不为空，则将内容编码设置为实体的内容编码
        if (entity.getContentEncoding() != null) {
            contentEncoding = entity.getContentEncoding().getValue();
            Log.d(TAG, "encoding: " + contentEncoding);
        }
        // 获取响应内容的输入流
        InputStream input = entity.getContent();
        // 如果内容编码为gzip，则使用GZIPInputStream解压缩输入流
        if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
            input = new GZIPInputStream(entity.getContent());
        }
        // 如果内容编码为deflate，则使用InflaterInputStream解压缩输入流
        else if (contentEncoding != null && contentEncoding.equalsIgnoreCase("deflate")) {
            Inflater inflater = new Inflater(true);
            input = new InflaterInputStream(entity.getContent(), inflater);
        }
        try {
            // 将输入流转换成字符流
            InputStreamReader isr = new InputStreamReader(input);
            // 使用缓冲读取器从字符流中读取响应内容
            BufferedReader br = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            while (true) {
                String buff = br.readLine();
                if (buff == null) {
                    // 如果读取完毕，则返回响应内容的字符串
                    return sb.toString();
                }
                // 将读取到的内容添加到字符串构建器中
                sb = sb.append(buff);
            }
        } finally {
            // 关闭输入流
            input.close();
        }
    }

    // 发送POST请求并获取响应内容
    private JSONObject postRequest(JSONObject js) throws NetworkFailureException {
        // 如果没有登录，则抛出异常
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }
        // 创建HttpPost对象
        HttpPost httpPost = createHttpPost();
        try {
            // 创建参数列表
            LinkedList<BasicNameValuePair> list = new LinkedList<BasicNameValuePair>();
            // 将传入的JSONObject对象转换成字符串，并添加到参数列表中
            list.add(new BasicNameValuePair("r", js.toString()));
            // 创建UrlEncodedFormEntity对象，并指定字符编码为UTF-8
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, "UTF-8");
            // 将UrlEncodedFormEntity对象设置为HttpPost对象的实体
            httpPost.setEntity(entity);
            // 执行POST请求，并获取响应对象
            HttpResponse response = mHttpClient.execute(httpPost);
            // 获取响应内容，并将其转换成JSONObject对象
            String jsString = getResponseContent(response.getEntity());
            return new JSONObject(jsString);
        } catch (ClientProtocolException e) {
            // 发送请求失败，抛出异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (IOException e) {
            // 发送请求失败，抛出异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("postRequest failed");
        } catch (JSONException e) {
            // 响应内容无法转换为JSONObject对象，抛出异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("unable to convert response content to jsonobject");
        } catch (Exception e) {
            // 发送请求失败，抛出异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("error occurs when posting request");
        }
    }

    /******/
    /**
     * 创建任务
     * @param task 任务对象
     * @throws NetworkFailureException 网络异常
     */
    public void createTask(Task task) throws NetworkFailureException {
        // 提交更新
        commitUpdate();
        try {
            // 创建JSONObject对象
            JSONObject jsPost = new JSONObject();
            // 创建JSONArray对象
            JSONArray actionList = new JSONArray();

            // 将新建任务的操作添加到actionList中
            actionList.put(task.getCreateAction(getActionId()));
            // 将actionList添加到JSONObject中
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 将客户端版本号添加到JSONObject中
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送post请求，并获取响应数据
            JSONObject jsResponse = postRequest(jsPost);
            // 获取响应结果中的第一个JSONObject对象
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            // 将新建任务的gid设置为返回结果中的new_id
            task.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            // 记录异常日志
            Log.e(TAG, e.toString());
            e.printStackTrace();
            // 抛出操作失败异常
            throw new ActionFailureException("create task: handing jsonobject failed");
        }
    }

    /**
     * 创建任务列表
     * @param tasklist 任务列表对象
     * @throws NetworkFailureException 网络异常
     */
    public void createTaskList(TaskList tasklist) throws NetworkFailureException {
        // 提交更新
        commitUpdate();
        try {
            // 创建JSONObject对象
            JSONObject jsPost = new JSONObject();
            // 创建JSONArray对象
            JSONArray actionList = new JSONArray();

            // 将新建任务列表的操作添加到actionList中
            actionList.put(tasklist.getCreateAction(getActionId()));
            // 将actionList添加到JSONObject中
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // 将客户端版本号添加到JSONObject中
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送post请求，并获取响应数据
            JSONObject jsResponse = postRequest(jsPost);
            // 获取响应结果中的第一个JSONObject对象
            JSONObject jsResult = (JSONObject) jsResponse.getJSONArray(
                    GTaskStringUtils.GTASK_JSON_RESULTS).get(0);
            // 将新建任务列表的gid设置为返回结果中的new_id
            tasklist.setGid(jsResult.getString(GTaskStringUtils.GTASK_JSON_NEW_ID));

        } catch (JSONException e) {
            // 记录异常日志
            Log.e(TAG, e.toString());
            e.printStackTrace();
            // 抛出操作失败异常
            throw new ActionFailureException("create tasklist: handing jsonobject failed");
        }
    }


    /**
     * 提交更新
     * @throws NetworkFailureException 网络异常
     */
    public void commitUpdate() throws NetworkFailureException {
        // 如果有更新操作
        if (mUpdateArray != null) {
            try {
                // 创建JSONObject对象
                JSONObject jsPost = new JSONObject();

                // 将更新操作添加到actionList中
                jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, mUpdateArray);

                // 将客户端版本号添加到JSONObject中
                jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

                // 发送post请求
                postRequest(jsPost);
                // 更新操作数组置空
                mUpdateArray = null;
            } catch (JSONException e) {
                // 记录异常日志
                Log.e(TAG, e.toString());
                e.printStackTrace();
                // 抛出操作失败异常
                throw new ActionFailureException("commit update: handing jsonobject failed");
            }
        }
    }


    /**
     * 添加更新节点到更新数组中。
     * @param node 要添加的节点。
     * @throws NetworkFailureException 网络故障异常。
     */
    public void addUpdateNode(Node node) throws NetworkFailureException {
        if (node != null) {
            // 如果更新数组中的项目太多可能会导致错误
            // 将最大项设置为10项
            if (mUpdateArray != null && mUpdateArray.length() > 10) {
                commitUpdate(); // 提交更新数组
            }

            if (mUpdateArray == null)
                mUpdateArray = new JSONArray(); // 创建一个新的JSONArray
            mUpdateArray.put(node.getUpdateAction(getActionId())); // 将节点的更新操作添加到JSONArray中
        }
    }

    /**
     * 移动任务到指定的父任务列表中。
     * @param task 要移动的任务。
     * @param preParent 移动前的父任务列表。
     * @param curParent 移动后的父任务列表。
     * @throws NetworkFailureException 网络故障异常。
     */
    public void moveTask(Task task, TaskList preParent, TaskList curParent)
            throws NetworkFailureException {
        commitUpdate(); // 提交更新数组
        try {
            JSONObject jsPost = new JSONObject(); // 创建一个新的JSONObject
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            // action_list
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_MOVE); // 设置操作类型为移动
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId()); // 设置操作ID
            action.put(GTaskStringUtils.GTASK_JSON_ID, task.getGid()); // 设置任务ID
            if (preParent == curParent && task.getPriorSibling() != null) {
                // 如果在同一个任务列表中移动且不是第一个，则设置prioring_sibing_id
                action.put(GTaskStringUtils.GTASK_JSON_PRIOR_SIBLING_ID, task.getPriorSibling());
            }
            action.put(GTaskStringUtils.GTASK_JSON_SOURCE_LIST, preParent.getGid()); // 设置移动前的任务列表ID
            action.put(GTaskStringUtils.GTASK_JSON_DEST_PARENT, curParent.getGid()); // 设置移动后的父任务列表ID
            if (preParent != curParent) {
                // 如果在不同的任务列表中移动，则设置dest_list
                action.put(GTaskStringUtils.GTASK_JSON_DEST_LIST, curParent.getGid());
            }
            actionList.put(action); // 将操作添加到操作列表中
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion); // 设置客户端版本号

            postRequest(jsPost); // 发送POST请求

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("move task: handing jsonobject failed"); // 抛出操作失败异常
        }
    }

    /**
     * 删除节点。
     * @param node 要删除的节点。
     * @throws NetworkFailureException 网络故障异常。
     */
    public void deleteNode(Node node) throws NetworkFailureException {
        commitUpdate(); // 提交更新数组
        try {
            JSONObject jsPost = new JSONObject(); // 创建一个新的JSONObject
            JSONArray actionList = new JSONArray();

            // action_list
            node.setDeleted(true); // 设置节点为已删除
            actionList.put(node.getUpdateAction(getActionId())); // 将节点的更新操作添加到操作列表中
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            // client_version
            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion); // 设置客户端版本号

            postRequest(jsPost); // 发送POST请求
            mUpdateArray = null; // 将更新数组设置为null

        } catch (JSONException e) {
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("delete node: handing jsonobject failed"); // 抛出操作失败异常
        }
    }


    /**
     * 获取任务列表
     *
     * @return 任务列表的JSONArray对象
     * @throws NetworkFailureException 网络连接异常
     */
    public JSONArray getTaskLists() throws NetworkFailureException {
        // 如果未登录，则抛出异常
        if (!mLoggedin) {
            Log.e(TAG, "please login first");
            throw new ActionFailureException("not logged in");
        }

        try {
            // 创建HttpGet对象，用于向服务器发送GET请求
            HttpGet httpGet = new HttpGet(mGetUrl);
            HttpResponse response = null;
            response = mHttpClient.execute(httpGet);

            // 获取任务列表
            String resString = getResponseContent(response.getEntity());
            String jsBegin = "_setup(";
            String jsEnd = ")}</script>";
            int begin = resString.indexOf(jsBegin);
            int end = resString.lastIndexOf(jsEnd);
            String jsString = null;
            if (begin != -1 && end != -1 && begin < end) {
                jsString = resString.substring(begin + jsBegin.length(), end);
            }
            JSONObject js = new JSONObject(jsString);
            return js.getJSONObject("t").getJSONArray(GTaskStringUtils.GTASK_JSON_LISTS);
        } catch (ClientProtocolException e) {
            // 如果出现ClientProtocolException，则抛出网络连接异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (IOException e) {
            // 如果出现IOException，则抛出网络连接异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new NetworkFailureException("gettasklists: httpget failed");
        } catch (JSONException e) {
            // 如果出现JSONException，则抛出操作失败异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task lists: handing jasonobject failed");
        }
    }

    /**
     * 根据任务列表ID获取任务列表
     *
     * @param listGid 任务列表ID
     * @return 任务列表的JSONArray对象
     * @throws NetworkFailureException 网络连接异常
     */
    public JSONArray getTaskList(String listGid) throws NetworkFailureException {
        commitUpdate();
        try {
            // 创建JSONObject对象，设置请求参数
            JSONObject jsPost = new JSONObject();
            JSONArray actionList = new JSONArray();
            JSONObject action = new JSONObject();

            action.put(GTaskStringUtils.GTASK_JSON_ACTION_TYPE,
                    GTaskStringUtils.GTASK_JSON_ACTION_TYPE_GETALL);
            action.put(GTaskStringUtils.GTASK_JSON_ACTION_ID, getActionId());
            action.put(GTaskStringUtils.GTASK_JSON_LIST_ID, listGid);
            action.put(GTaskStringUtils.GTASK_JSON_GET_DELETED, false);
            actionList.put(action);
            jsPost.put(GTaskStringUtils.GTASK_JSON_ACTION_LIST, actionList);

            jsPost.put(GTaskStringUtils.GTASK_JSON_CLIENT_VERSION, mClientVersion);

            // 发送POST请求，获取响应JSONObject对象
            JSONObject jsResponse = postRequest(jsPost);
            return jsResponse.getJSONArray(GTaskStringUtils.GTASK_JSON_TASKS);
        } catch (JSONException e) {
            // 如果出现JSONException，则抛出操作失败异常
            Log.e(TAG, e.toString());
            e.printStackTrace();
            throw new ActionFailureException("get task list: handing jsonobject failed");
        }
    }

    /**
     * 获取同步账户
     *
     * @return 同步账户对象
     */
    public Account getSyncAccount() {
        return mAccount;
    }

    /**
     * 重置更新数组
     */
    public void resetUpdateArray() {
        mUpdateArray = null;
    }
}
