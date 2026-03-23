package com.example.myapplication5;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.gpu.GpuDelegateFactory;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Detector {

    public interface DetectorListener {
        void onEmptyDetect();
        void onDetect(List<BoundingBox> boundingBoxes, long inferenceTime);
    }

    private static final float INPUT_MEAN = 0f;
    private static final float INPUT_STD = 255f;
    private static final DataType INPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final float CONFIDENCE_THRESHOLD = 0.3F;
    private static final float IOU_THRESHOLD = 0.5F;

    private Interpreter interpreter;
    private List<String> labels = new ArrayList<>();
    private int tensorWidth, tensorHeight, numChannel, numElements;
    private ImageProcessor imageProcessor;
    private Context context;
    private String modelPath, labelPath;
    private DetectorListener detectorListener;

    public Detector(Context context,
                    String modelPath,
                    String labelPath,
                    DetectorListener listener) {
        this.context = context;
        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.detectorListener = listener;

        // 1) Configure Interpreter options with GPU delegate if supported
        CompatibilityList compatList = new CompatibilityList();
        Interpreter.Options options = new Interpreter.Options();
        if (compatList.isDelegateSupportedOnThisDevice()) {
            // Use the deprecated-but-available GpuDelegate.Options class
            GpuDelegate.Options gpuOptions = new GpuDelegate.Options();
            // tweak as needed:
            gpuOptions.setPrecisionLossAllowed(true);
            gpuOptions.setInferencePreference(
                    GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED
            );
            GpuDelegate gpuDelegate = new GpuDelegate(gpuOptions);
            options.addDelegate(gpuDelegate);
        } else {
            options.setNumThreads(4);
        }

        // 2) Load the .tflite model
        MappedByteBuffer model = null;
        try {
            model = FileUtil.loadMappedFile(context, modelPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        interpreter = new Interpreter(model, options);

        // 3) Read input & output tensor shapes
        int[] inShape = interpreter.getInputTensor(0).shape();
        tensorWidth  = inShape[1];
        tensorHeight = inShape[2];
        if (inShape[1] == 3) {
            tensorWidth  = inShape[2];
            tensorHeight = inShape[3];
        }
        int[] outShape = interpreter.getOutputTensor(0).shape();
        numChannel  = outShape[1];
        numElements = outShape[2];

        // 4) Load label file
        try (InputStream is = context.getAssets().open(labelPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                labels.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 5) Build the image preprocessor
        imageProcessor = new ImageProcessor.Builder()
                .add(new NormalizeOp(INPUT_MEAN, INPUT_STD))
                .add(new CastOp(INPUT_IMAGE_TYPE))
                .build();
    }

    /** Restart the interpreter, optionally with GPU again */
    public void restart(boolean useGpu) {
        interpreter.close();
        Interpreter.Options options = new Interpreter.Options();
        if (useGpu) {
            CompatibilityList compatList = new CompatibilityList();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                GpuDelegate.Options gpuOptions = new GpuDelegate.Options();
                gpuOptions.setPrecisionLossAllowed(true);
                gpuOptions.setInferencePreference(
                        GpuDelegateFactory.Options.INFERENCE_PREFERENCE_SUSTAINED_SPEED
                );
                GpuDelegate gpuDelegate = new GpuDelegate(gpuOptions);
                options.addDelegate(gpuDelegate);
            } else {
                options.setNumThreads(4);
            }
        } else {
            options.setNumThreads(4);
        }

        MappedByteBuffer model = null;
        try {
            model = FileUtil.loadMappedFile(context, modelPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        interpreter = new Interpreter(model, options);
    }

    /** Properly close interpreter */
    public void close() {
        interpreter.close();
    }

    /** Run one frame through the model */
    public void detect(Bitmap frame) {
        if (tensorWidth==0 || tensorHeight==0 || numChannel==0 || numElements==0) return;

        long startTime = SystemClock.uptimeMillis();

        Bitmap resized = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false);
        TensorImage ti = new TensorImage(INPUT_IMAGE_TYPE);
        ti.load(resized);
        TensorImage processed = imageProcessor.process(ti);

        TensorBuffer output = TensorBuffer.createFixedSize(
                new int[]{1, numChannel, numElements},
                OUTPUT_IMAGE_TYPE
        );
        interpreter.run(processed.getBuffer(), output.getBuffer());

        List<BoundingBox> boxes = bestBox(output.getFloatArray());
        long inferenceTime = SystemClock.uptimeMillis() - startTime;

        if (boxes == null) {
            detectorListener.onEmptyDetect();
        } else {
            detectorListener.onDetect(boxes, inferenceTime);
        }
    }

    /** Find highest-confidence boxes + apply NMS */
    private List<BoundingBox> bestBox(float[] arr) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (int c = 0; c < numElements; c++) {
            float maxConf = CONFIDENCE_THRESHOLD;
            int maxIdx = -1;
            for (int j = 4; j < numChannel; j++) {
                int idx = c + numElements * j;
                if (arr[idx] > maxConf) {
                    maxConf = arr[idx];
                    maxIdx = j - 4;
                }
            }
            if (maxConf > CONFIDENCE_THRESHOLD) {
                String cls = labels.get(maxIdx);
                float cx = arr[c];
                float cy = arr[c + numElements];
                float w  = arr[c + numElements*2];
                float h  = arr[c + numElements*3];
                float x1 = cx - w/2f, y1 = cy - h/2f;
                float x2 = cx + w/2f, y2 = cy + h/2f;
                if (x1 < 0 || x2 > 1 || y1 < 0 || y2 > 1) continue;
                boxes.add(new BoundingBox(x1,y1,x2,y2,cx,cy,w,h,maxConf,maxIdx,cls));
            }
        }
        return boxes.isEmpty() ? null : applyNMS(boxes);
    }

    private List<BoundingBox> applyNMS(List<BoundingBox> boxes) {
        List<BoundingBox> sorted = new ArrayList<>(boxes);
        sorted.sort((a,b) -> Float.compare(b.cnf, a.cnf));
        List<BoundingBox> picked = new ArrayList<>();
        while (!sorted.isEmpty()) {
            BoundingBox first = sorted.remove(0);
            picked.add(first);
            Iterator<BoundingBox> it = sorted.iterator();
            while (it.hasNext()) {
                if (calculateIoU(first, it.next()) >= IOU_THRESHOLD) {
                    it.remove();
                }
            }
        }
        return picked;
    }

    private float calculateIoU(BoundingBox a, BoundingBox b) {
        float x1 = Math.max(a.x1, b.x1);
        float y1 = Math.max(a.y1, b.y1);
        float x2 = Math.min(a.x2, b.x2);
        float y2 = Math.min(a.y2, b.y2);
        float inter = Math.max(0f, x2 - x1) * Math.max(0f, y2 - y1);
        float areaA = a.w * a.h;
        float areaB = b.w * b.h;
        return inter / (areaA + areaB - inter);
    }
}
