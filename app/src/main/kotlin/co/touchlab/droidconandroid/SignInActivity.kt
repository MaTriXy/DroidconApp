package co.touchlab.droidconandroid

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentSender
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import co.touchlab.android.threading.tasks.TaskQueue
import co.touchlab.droidconandroid.presenter.LoginScreenPresenter
import co.touchlab.droidconandroid.tasks.RunGoogleLoginTask
import co.touchlab.droidconandroid.utils.Toaster
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.plus.Plus

/**
 *
 * Created by izzyoji :) on 7/23/15.
 */
public class SignInActivity : AppCompatActivity(), LoginScreenPresenter.Host {

    public companion object {
        val REQUEST_CODE_RESOLVE_ERR = 9000
        val DIALOG_ERROR = "dialog_error";
        val SCOPE: String = "audience:server:client_id:654878069390-ft2vt5sp4v0pcfk4poausabjnah0aeod.apps.googleusercontent.com"
        val REQUEST_RESOLVE_ERROR = 1001;
        var googleApiClient: GoogleApiClient? = null

        public fun getLaunchIntent(c: Context): Intent {
            return Intent(c, SignInActivity::class.java)
        }
    }

    private var okButton: Button? = null
    private var progressBar: View? = null
    private var accountAdapter: AccountAdapter? = null
    private var listView: ListView? = null

    private var mResolvingError = false;

    private var presenter: LoginScreenPresenter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in);

        presenter = LoginScreenPresenter(this, this)
        listView = findViewById(R.id.list) as ListView;


        listView!!.setOnItemClickListener { adapterView: AdapterView<*>, view1: View, i: Int, l: Long ->
            accountAdapter!!.setSelectedAccount(i)
            okButton!!.setEnabled(true);
        }

        progressBar = findView(R.id.progress)
        (progressBar as ProgressBar).getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

        okButton = findView(R.id.ok) as Button
        (okButton as Button).setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                googleApiClient = GoogleApiClient.Builder(this@SignInActivity)
                        .addConnectionCallbacks(ConnectionCallbacksImpl())
                        .addOnConnectionFailedListener(OnConnectionFailedListenerImpl())
                        .addApi(Plus.API)
                        .setAccountName(accountAdapter!!.getSelectedAccount())
                        .addScope(Plus.SCOPE_PLUS_LOGIN)
                        .build()!!
                forceGoogleConnect()

                okButton!!.setEnabled(false);
                progressBar!!.setVisibility(View.VISIBLE)
            }
        })

        findView(R.id.cancel).setOnClickListener{
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val accounts = AccountManager.get(this).getAccountsByType("com.google")

        accountAdapter = AccountAdapter(this, accounts, R.layout.item_account)

        listView!!.setAdapter(accountAdapter)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_RESOLVE_ERR) {
            if (resultCode == Activity.RESULT_OK) {
                googleClientConnect()
            }
        }
    }

    override fun onLoginReturned(failed: Boolean, firstLogin: Boolean)
    {
        if (!failed) {
            finish()
            startScheduleActivity(this)
            if (firstLogin)
                createEditUserProfile(this)
        }
        else
        {
            okButton!!.setEnabled(true);
            progressBar!!.setVisibility(View.GONE)
            Toaster.showMessage(this@SignInActivity, R.string.google_error)
        }
    }

    override fun onStop() {
        super.onStop()
        googleDisconnectIfConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter!!.unregister()
        presenter = null
    }

    fun googleClientConnect() {
        googleApiClient!!.connect()
    }

    private fun googleDisconnectIfConnected() {
        if (googleApiClient != null && googleApiClient!!.isConnected())
            googleApiClient!!.disconnect()
    }

    public fun forceGoogleConnect() {
        googleDisconnectIfConnected()
        googleApiClient!!.connect()
    }

    private val PROFILE_PIC_SIZE: Int = 100

    public inner class ConnectionCallbacksImpl() : GoogleApiClient.ConnectionCallbacks {
        override fun onConnected(bundle: Bundle?) {
            val accountName = Plus.AccountApi.getAccountName(googleApiClient)
            val person = Plus.PeopleApi.getCurrentPerson(googleApiClient)
            var imageURL: String? = null
            var coverURL: String? = null
            if (person != null && person.hasImage()) {
                val image = person.getImage()

                if (image != null && image.hasUrl()) {
                    val url = image.getUrl()
                    imageURL = url.substring(0, url.length - 2) + PROFILE_PIC_SIZE;
                }

                val cover = person.getCover()
                if(cover != null && cover.getCoverPhoto() != null && cover.getCoverPhoto().hasUrl())
                {
                    coverURL = cover.getCoverPhoto().getUrl();
                }
            }

            val runGoogle = RunGoogleLoginTask(accountName, presenter, person.displayName, imageURL, coverURL)
            TaskQueue.loadQueueNetwork(this@SignInActivity).execute(runGoogle)
        }

        override fun onConnectionSuspended(i: Int) {
        }
    }

    public inner class OnConnectionFailedListenerImpl() : GoogleApiClient.OnConnectionFailedListener {
        override fun onConnectionFailed(result: ConnectionResult) {
            if(mResolvingError)
            {
                return
            }
            else if (result != null && result.hasResolution()) {
                mResolvingError = true;
                try {
                    result.startResolutionForResult(this@SignInActivity, SignInActivity.REQUEST_CODE_RESOLVE_ERR)
                } catch (e: IntentSender.SendIntentException) {
                    googleApiClient!!.connect()
                }

            } else {
                if(result != null) {
                    showErrorDialog(result.getErrorCode());
                    mResolvingError = true;
                }
                else
                {
                    Toaster.showMessage(this@SignInActivity, R.string.google_error)
                }
            }

            okButton!!.setEnabled(true);
            progressBar!!.setVisibility(View.GONE)
        }


    }

    fun showErrorDialog( errorCode: Int) {
        // Create a fragment for the error dialog
        val dialogFragment = ErrorDialogFragment();
        // Pass the error that should be displayed
        val args = Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        try {
            dialogFragment.show(getSupportFragmentManager(), "errordialog");
        }
        catch (e: Exception)
        {
            //Meh
        }

    }

    fun onDialogDismissed()
    {
        mResolvingError = false;
    }

    inner class ErrorDialogFragment : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        override fun onDismiss(dialog: DialogInterface?) {
            onDialogDismissed();
        }
    }
}

class AccountAdapter : ArrayAdapter<Account> {

    private var inflater: LayoutInflater
    private var selectedPos: Int = -1
    private var resource: Int

    constructor(context: Context, accounts: Array<Account>, resource: Int) : super(context, resource, accounts) {

        inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        this.resource = resource;
    }


    fun getSelectedAccount(): String {
        return getItem(selectedPos).name
    }

    fun setSelectedAccount(position: Int) {
        selectedPos = position
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {

        var view: View
        if (convertView == null) {
            view = inflater.inflate(resource, parent, false)
        } else {
            view = convertView
        }

        var name = view.findViewById(R.id.account) as TextView
        name.setText(getItem(position).name)

        var radioButton = view.findViewById(R.id.radio) as RadioButton
        radioButton.setChecked(selectedPos == position)
        radioButton.setClickable(false)

        return view;
    }
}
