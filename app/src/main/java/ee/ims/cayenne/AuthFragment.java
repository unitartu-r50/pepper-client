package ee.ims.cayenne;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class AuthFragment extends DialogFragment {
    private static String auth_code;
    private static String help_text;


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        LayoutInflater inflater = getActivity().getLayoutInflater();
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getActivity());
        View view = inflater.inflate(R.layout.fragment_auth, null);
        dialogBuilder.setView(view);
        ((TextView)view.findViewById(R.id.auth_string)).setText(auth_code);
        ((TextView)view.findViewById(R.id.auth_helptext)).setText(help_text);
        return dialogBuilder.create();
    }

    public static AuthFragment newInstance(String code, String text) {
        AuthFragment authFragment = new AuthFragment();
        auth_code = code;
        help_text = text;
        return authFragment;
    }
}
