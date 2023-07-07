package org.camicroscope;

import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.FormatTools; // https://downloads.openmicroscopy.org/bio-formats/latest/api/loci/formats/FormatTools.html
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString; // Watch out for memory leaks
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCBytes; // Likewise
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCBoolean;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toJavaString;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.WordFactory;

// TODO: we return error messages in C pointers; how to free these from C?
// We might also want to use a try-let before we use .get() on returning strings

public class BFBridge {

    // TODO: consider providing more metadata
    // https://bio-formats.readthedocs.io/en/latest/metadata-summary.html

    private static IFormatReader reader = new ImageReader();
    private static String lastError = "";
    private static byte[] readBuffer = new byte[10000000];

    @CEntryPoint(name = "bf_get_error")
    static CCharPointer BFGetError(IsolateThread t) {
        return toCString(lastError).get();
    }

    @CEntryPoint(name = "bf_open")
    static byte BFOpen(IsolateThread t, CCharPointer filePath) {
        try {
            // Use the easier resolution API
            reader.setFlattenedResolutions(false);
            reader.setId(toJavaString(filePath));
            return toCBoolean(true);
        } catch (Exception e) {
            lastError = e.toString();
            return toCBoolean(false);
        }
    }

    // If expected to be the single file, or always true for single-file formats
    @CEntryPoint(name = "bf_is_single_file")
    static byte BFIIsSingleFile(IsolateThread t, CCharPointer filePath) {
        try {
            return toCBoolean(reader.isSingleFile(toJavaString(filePath)));
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_close")
    static byte BFClose(IsolateThread t, int fileOnly) {
        try {
            reader.close(fileOnly != 0);
            return toCBoolean(true);
        } catch (Exception e) {
            lastError = e.toString();
            return toCBoolean(false);
        }
    }

    @CEntryPoint(name = "bf_get_resolution_count")
    static int BFGetResolutionCount(IsolateThread t) {
        try {
            // In resolution mode, each of series has a number of resolutions
            // WSI pyramids have multiple and others have one
            // This method returns resolution counts for the current series
            return reader.getResolutionCount();
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_set_resolution_count")
    static byte BFSetCurrentResolution(IsolateThread t, int resIndex) {
        try {
            // Precondition: The caller must check that at least 0 and less than
            // resolutionCount
            reader.setResolution(resIndex);
            return toCBoolean(true);
        } catch (Exception e) {
            lastError = e.toString();
            return toCBoolean(false);
        }
    }

    @CEntryPoint(name = "bf_get_size_x")
    static int BFGetSizeX(IsolateThread t) {
        try {
            // For current resolution
            return reader.getSizeX();
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_get_size_y")
    static int BFGetSizeY(IsolateThread t) {
        try {
            return reader.getSizeY();
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_get_optimal_tile_Width")
    static int BFGetOptimalTileWidth(IsolateThread t) {
        try {
            return reader.getOptimalTileWidth();
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_get_optimal_tile_height")
    static int BFGetOptimalTİleHeight(IsolateThread t) {
        try {
            return reader.getOptimalTileHeight();
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_get_format")
    static CCharPointer BFGetFormat(IsolateThread t) {
        try {
            return toCString(reader.getFormat()).get();
        } catch (Exception e) {
            lastError = e.toString();
            return toCString("").get();
        }
    }

    @CEntryPoint(name = "bf_get_pixel_type")
    static int BFGetPixelType(IsolateThread t) {
        try {
            return reader.getPixelType();
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_get_bits_per_pixel")
    static int BFGetBitsPerPixel(IsolateThread t) {
        try {
            return reader.getBitsPerPixel();
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_get_bytes_per_pixel")
    static int BFGetBytesPerPixel(IsolateThread t) {
        try {
            return FormatTools.getBytesPerPixel(reader.getPixelType());
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_get_rgb_channel_count")
    static int BFGetRGBChannelCount(IsolateThread t) {
        try {
            return reader.getRGBChannelCount();
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_is_rgb")
    static byte BFIsRGB(IsolateThread t) {
        try {
            return toCBoolean(reader.isRGB());
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_is_interleaved")
    static byte BFIsInterleaved(IsolateThread t) {
        try {
            return toCBoolean(reader.isInterleaved());
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_is_little_endian")
    static byte BFIsLittleEndian(IsolateThread t) {
        try {
            return toCBoolean(reader.isLittleEndian());
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_is_floating_point")
    static byte BFIsFloatingPoint(IsolateThread t) {
        try {
            return toCBoolean(FormatTools.isFloatingPoint(reader));
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bf_get_dimension_order")
    static CCharPointer BFGetDimensionOrder(IsolateThread t) {
        try {
            return toCString(reader.getDimensionOrder()).get();
        } catch (Exception e) {
            lastError = e.toString();
            // TODO: use null pointer
            return toCString("").get();
        }
    }

    // TODO: verify that returning null pointer is OKAY
    // TODO: return a 10MB array 100 times and see if there are memory leaks
    @CEntryPoint(name = "bf_open_bytes")
    static CCharPointer BFOpenBytes(IsolateThread t, int x, int y, int w, int h) {
        try {
            int size = w * h * FormatTools.getBytesPerPixel(reader.getPixelType()) * reader.getRGBChannelCount();
            if (size > readBuffer.length) {
                lastError = "Requested tile too big; must be at most " + readBuffer.length + " bytes";
                return toCString("").get();
            }
            // TODO: do we need to handle other planes here?
            reader.openBytes(0, readBuffer, x, y, w, h);
            // Erase previous responses
            for (int i = size; i < readBuffer.length; i++) {
                readBuffer[i] = 0;
            }
            return toCBytes(readBuffer).get();
        } catch (Exception e) {
            lastError = e.toString();
            // This is permitted: https://github.com/oracle/graal/blob/492c6016c5d9233be5de2dd9502cc81f746fc8e7/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/c/CTypeConversionSupportImpl.java#L55
            return WordFactory.nullPointer();
        }
    }

    // Debug function
    public static byte openFile(String filename) throws Exception {
        try {
                        System.out.println("start 1");
            reader.close(true);
                                    System.out.println("start 2");

            reader.setId("/Users/zerf/Downloads/Github-repos/CGDogan/camic-Distro/images/posdebugfiles_2.dcm");
                                    System.out.println("start 3");

            reader.openBytes(0);
                                    System.out.println("start 4");

            return 0;
        } catch (Exception e) {
            System.out.println("excepting incoming");
            throw e;
        }
    }

    public static void main(String args[]) throws Exception {
        int x = 10;
        int y = 25;
        int z = x + y;

        openFile("");
        // System.out.println("Sum of x+y = " + z);
    }
}
