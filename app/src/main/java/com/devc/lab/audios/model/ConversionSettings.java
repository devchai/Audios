package com.devc.lab.audios.model;

public class ConversionSettings {
    public enum AudioFormat {
        M4A("m4a");
        
        private final String extension;
        
        AudioFormat(String extension) {
            this.extension = extension;
        }
        
        public String getExtension() {
            return extension;
        }
    }
    
    private AudioFormat format;
    private int bitrate;
    private int sampleRate;
    private String inputPath;
    private String outputPath;
    
    public ConversionSettings() {
        // 기본 설정
        this.format = AudioFormat.M4A;
        this.bitrate = 128;
        this.sampleRate = 44100;
    }
    
    // Getters and Setters
    public AudioFormat getFormat() {
        return format;
    }
    
    public void setFormat(AudioFormat format) {
        this.format = format;
    }
    
    public int getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(int bitrate) {
        this.bitrate = bitrate;
    }
    
    public int getSampleRate() {
        return sampleRate;
    }
    
    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }
    
    public String getInputPath() {
        return inputPath;
    }
    
    public void setInputPath(String inputPath) {
        this.inputPath = inputPath;
    }
    
    public String getOutputPath() {
        return outputPath;
    }
    
    public void setOutputPath(String outputPath) {
        this.outputPath = outputPath;
    }
    
    @Override
    public String toString() {
        return "ConversionSettings{" +
                "format=" + format +
                ", bitrate=" + bitrate +
                ", sampleRate=" + sampleRate +
                ", inputPath='" + inputPath + '\'' +
                ", outputPath='" + outputPath + '\'' +
                '}';
    }
}