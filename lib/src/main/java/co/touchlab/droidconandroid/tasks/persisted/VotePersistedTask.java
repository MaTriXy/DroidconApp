package co.touchlab.droidconandroid.tasks.persisted;
import android.app.Application;
import android.content.Context;

import co.touchlab.android.threading.eventbus.EventBusExt;
import co.touchlab.android.threading.tasks.persisted.ConfigException;
import co.touchlab.android.threading.tasks.persisted.PersistedTask;
import co.touchlab.android.threading.tasks.persisted.PersistedTaskQueue;
import co.touchlab.android.threading.tasks.persisted.PersistedTaskQueueConfig;
import co.touchlab.droidconandroid.CrashReport;
import co.touchlab.droidconandroid.presenter.AppManager;
import retrofit.RetrofitError;

/**
 * Created by toidiu on 11/12/15.
 */
abstract public class VotePersistedTask extends PersistedTask
{

    protected abstract String errorString();


    private static PersistedTaskQueue VOTE_INSTANCE;

    public static synchronized PersistedTaskQueue getQueue(Context context)
    {
        if(VOTE_INSTANCE == null)
        {
            PersistedTaskQueueConfig build;
            try
            {
                build = new PersistedTaskQueueConfig.Builder()
                        .addQueueListener(new BackoffRetryListener()).build(context);
            }
            catch(ConfigException e)
            {
                throw new RuntimeException(e);
            }
            VOTE_INSTANCE = new PersistedTaskQueue((Application)context.getApplicationContext(), build);
        }

        return VOTE_INSTANCE;
    }

    @Override
    protected void onPermanentError(Context context, Throwable exception)
    {
        super.onPermanentError(context, exception);
    }

    @Override
    protected final boolean handleError(Context context, Throwable e)
    {
        if(e instanceof RetrofitError)
        {
            EventBusExt.getDefault().post(new VoteError(
                    AppManager.getPlatformClient().getString("network_msg")));
            return true;
        }
        else
        {
            EventBusExt.getDefault().post(new VoteError(true, errorString()));
            CrashReport.logException(e);
            return false;
        }
    }

    //CLASS--------------
    public static class VoteError
    {
        public boolean permanentErr = false;
        public String errorMsg;

        public VoteError(String s)
        {
            errorMsg = s;
        }

        public VoteError(boolean b, String s)
        {
            permanentErr = b;
            errorMsg = s;
        }
    }

}
