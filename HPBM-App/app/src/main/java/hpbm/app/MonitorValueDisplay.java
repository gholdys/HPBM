package hpbm.app;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;

public class MonitorValueDisplay extends LinearLayout {

    private static final String NAMESPACE = "http://schemas.android.com/apk/res-auto";

    private TextView labelView;
    private TextView valueView;
    private String formatString;


    public MonitorValueDisplay(Context context) {
        super(context);
        init(null, 0);
    }

    public MonitorValueDisplay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public MonitorValueDisplay(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        setOrientation( VERTICAL );

        Typeface font = Typeface.createFromAsset(getContext().getAssets(), "Comfortaa-Bold.ttf");

        labelView = new TextView(getContext());
        labelView.setText( attrs.getAttributeValue( NAMESPACE, "label" ) );
        labelView.setTypeface( font );
        labelView.setTextSize( TypedValue.COMPLEX_UNIT_SP, 16f );
        labelView.setGravity( Gravity.START );
        labelView.setTextAlignment( TEXT_ALIGNMENT_TEXT_START );

        valueView = new TextView(getContext());

        valueView.setTypeface( font );
        valueView.setTextSize( TypedValue.COMPLEX_UNIT_SP, 32f );
        valueView.setTextAlignment( TEXT_ALIGNMENT_CENTER );
        valueView.setGravity( Gravity.CENTER_HORIZONTAL );

        setFormatString( attrs.getAttributeValue( NAMESPACE, "format" ) );

        setValue( attrs.getAttributeValue( NAMESPACE, "value" ) );

        addView(labelView);
        addView(valueView);
    }

    public final void setFormatString(String formatString) {
        this.formatString = formatString;
    }

    public final String getFormatString() {
        return formatString;
    }

    public void setLabel( CharSequence label ) {
        labelView.setText( label );
    }

    public CharSequence getLabel() {
        return labelView.getText();
    }

    public final void setValue( Object... values ) {
        if ( values == null || (values.length == 1 && values[0] == null) ) {
            valueView.setText("");
        } else if ( formatString != null ) {
            valueView.setText( String.format(formatString, values) );
        } else if ( values.length == 1 ) {
            valueView.setText( values[0] != null ? values[0].toString() : "" );
        } else {
            valueView.setText( Arrays.toString( values ) );
        }
    }

    public CharSequence getValueText() {
        return valueView.getText();
    }

}
