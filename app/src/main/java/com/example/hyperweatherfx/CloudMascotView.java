package com.example.hyperweatherfx;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class CloudMascotView extends View {

    public interface OnMascotClickListener {
        void onClick();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnMascotClickListener clickListener;

    private float expression = 0.65f;
    private float downX;
    private float downY;
    private float mascotSize;
    private float touchSlop;
    private boolean moved;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLUE = 0xFF65A8F5;
    private static final int DARK = 0xFF403044;
    private static final int PINK = 0xFFFFA4B5;

    public CloudMascotView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context)
                .getScaledTouchSlop();
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("云朵小精灵，点击显示赞赏二维码");
    }

    public void setOnMascotClickListener(
            OnMascotClickListener listener
    ) {
        clickListener = listener;
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void color(int color) {
        paint.setColor(color);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(1);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        mascotSize = Math.min(
                dp(270),
                Math.min(getWidth() * 0.78f, getHeight() * 0.48f)
        );

        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(mascotSize / 270f, mascotSize / 270f);

        drawShadow(canvas);
        drawCloud(canvas);
        drawFace(canvas);
        drawScarf(canvas);

        canvas.restore();
    }

    private void drawShadow(Canvas canvas) {
        paint.setShader(new RadialGradient(
                0, 100, 112,
                new int[]{0x335B91E8, 0x005B91E8},
                null,
                Shader.TileMode.CLAMP
        ));

        canvas.drawOval(
                -112, 82, 112, 125, paint
        );

        paint.setShader(null);
    }

    private void drawCloud(Canvas canvas) {
        Path cloud = new Path();

        cloud.moveTo(-105, 38);
        cloud.cubicTo(-145, 25, -143, -28, -101, -44);
        cloud.cubicTo(-94, -91, -39, -113, -5, -88);
        cloud.cubicTo(27, -124, 88, -103, 94, -55);
        cloud.cubicTo(145, -40, 151, 20, 109, 43);
        cloud.cubicTo(88, 83, -78, 87, -105, 38);
        cloud.close();

        paint.setShader(new RadialGradient(
                0, -35, 180,
                new int[]{Color.WHITE, 0xFFF2F6FF, 0xFFDCE8FF},
                null,
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(cloud, paint);
        paint.setShader(null);

        paint.setColor(0xFFB6C9F2);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawPath(cloud, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawFace(Canvas canvas) {
        float happy = expression;

        // 脸颊
        color(PINK);
        paint.setAlpha((int) (110 + happy * 80));

        canvas.drawOval(-91, 8, -56, 29, paint);
        canvas.drawOval(56, 8, 91, 29, paint);

        paint.setAlpha(255);

        float eyeY = -4;

        if (happy > 0.65f) {
            // 开心的弯眼睛
            paint.setColor(DARK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(9);
            paint.setStrokeCap(Paint.Cap.ROUND);

            float arc = happy * 12;

            Path left = new Path();
            left.moveTo(-65, eyeY + 8);
            left.quadTo(-45, eyeY - arc, -25, eyeY + 8);
            canvas.drawPath(left, paint);

            Path right = new Path();
            right.moveTo(25, eyeY + 8);
            right.quadTo(45, eyeY - arc, 65, eyeY + 8);
            canvas.drawPath(right, paint);

            paint.setStyle(Paint.Style.FILL);

            // 开心的嘴巴
            paint.setColor(0xFFE96F80);
            canvas.drawOval(
                    -19, 20, 19, 53, paint
            );

            paint.setColor(0xFFFFB1B8);
            canvas.drawOval(
                    -12, 24, 12, 43, paint
            );

        } else {
            // 委屈的大眼睛
            float eyeScale = 1f + (1f - happy) * 0.18f;

            canvas.save();
            canvas.scale(eyeScale, eyeScale);

            drawEye(canvas, -43, eyeY);
            drawEye(canvas, 43, eyeY);

            canvas.restore();

            // 可怜巴巴的眉毛
            paint.setColor(DARK);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6);
            paint.setStrokeCap(Paint.Cap.ROUND);

            Path leftBrow = new Path();
            leftBrow.moveTo(-62, -35);
            leftBrow.quadTo(-46, -23, -30, -34);
            canvas.drawPath(leftBrow, paint);

            Path rightBrow = new Path();
            rightBrow.moveTo(30, -34);
            rightBrow.quadTo(46, -23, 62, -35);
            canvas.drawPath(rightBrow, paint);

            paint.setStyle(Paint.Style.FILL);

            // 委屈的小嘴巴
            paint.setColor(0xFF713C48);

            Path mouth = new Path();
            mouth.moveTo(-10, 35);
            mouth.quadTo(0, 22, 10, 35);
            canvas.drawPath(mouth, paint);
        }
    }

    private void drawEye(Canvas canvas, float x, float y) {
        color(0xFF44334F);
        canvas.drawOval(
                x - 15, y - 18,
                x + 15, y + 18, paint
        );

        color(0xFFFFFFFF);
        canvas.drawCircle(x - 5, y - 8, 5, paint);
        canvas.drawCircle(x + 6, y + 6, 2.5f, paint);

        // 眼睛下方的泪光
        if (expression < 0.25f) {
            color(0xFF85C6FF);
            canvas.drawCircle(
                    x + 17, y + 19, 4, paint
            );
        }
    }

    private void drawScarf(Canvas canvas) {
        // 围巾主体
        Path scarf = new Path();
        scarf.moveTo(-105, 65);
        scarf.quadTo(-60, 94, 0, 72);
        scarf.quadTo(62, 94, 105, 60);
        scarf.quadTo(115, 85, 82, 105);
        scarf.quadTo(0, 125, -85, 104);
        scarf.close();

        color(BLUE);
        canvas.drawPath(scarf, paint);

        // 围巾尾部
        Path tail = new Path();
        tail.moveTo(50, 85);
        tail.quadTo(95, 77, 101, 100);
        tail.lineTo(96, 139);
        tail.quadTo(72, 143, 54, 124);
        tail.close();

        color(0xFF4F98EB);
        canvas.drawPath(tail, paint);

        // 雪花装饰
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(4);
        paint.setStrokeCap(Paint.Cap.ROUND);

        canvas.drawLine(76, 101, 76, 122, paint);
        canvas.drawLine(66, 111, 86, 111, paint);
        canvas.drawLine(69, 104, 83, 118, paint);
        canvas.drawLine(83, 104, 69, 118, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        float distance = (float) Math.hypot(
                x - cx, y - cy
        );

        float radius = mascotSize * 0.5f;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                moved = false;
                updateExpression(distance, radius);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(
                        x - downX,
                        y - downY
                ) > touchSlop) {
                    moved = true;
                }

                updateExpression(distance, radius);
                return true;

            case MotionEvent.ACTION_UP:
                updateExpression(distance, radius);

                boolean tappedMascot =
                        !moved
                        && distance < radius * 0.78f;

                if (tappedMascot && clickListener != null) {
                    clickListener.onClick();
                }

                performClick();
                return true;

            case MotionEvent.ACTION_CANCEL:
                expression = 0.65f;
                invalidate();
                return true;
        }

        return true;
    }

    private void updateExpression(
            float distance, float radius
    ) {
        float value = 1f - (
                (distance - radius * 0.55f)
                        / (radius * 1.15f)
        );

        value = clamp(value);

        // 平滑过渡
        expression += (value - expression) * 0.35f;

        invalidate();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private float dp(float value) {
        return value * getResources()
                .getDisplayMetrics().density;
    }
}
