package org.camicroscope;

import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.FormatTools; // https://downloads.openmicroscopy.org/bio-formats/latest/api/loci/formats/FormatTools.html
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import ome.units.UNITS;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCString; // Watch out for memory leaks
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCBytes; // Likewise
import static org.graalvm.nativeimage.c.type.CTypeConversion.toCBoolean;
import static org.graalvm.nativeimage.c.type.CTypeConversion.toJavaString;

import loci.formats.tools.ImageConverter;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.file.Files;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.WordFactory;

// https://bio-formats.readthedocs.io/en/v6.14.0/developers/file-reader.html#reading-files
// There are other utilities as well

// How does GraalVM isolates and threads work?
// https://medium.com/graalvm/isolates-and-compressed-references-more-flexible-and-efficient-memory-management-for-graalvm-a044cc50b67e

// TODO: we return error messages in C pointers; how to free these from C?

// TODO: Test the bioformats server with the subresolutions.java divide-by-three file maybe?
// TODO: test reading a big file without subresolutions. we should handle
// to generate when big and when we don't have every layer with ratio 2.5 maybe or maybe 2.1

import loci.common.RandomAccessInputStream;

public class BFBridge {
    private static IFormatReader reader = new ImageReader();
    private static IMetadata metadata = MetadataTools.createOMEXMLMetadata();
    private static String lastError = "";
    private static byte[] communicationBuffer = new byte[10000000];

    @CEntryPoint(name = "bf_get_error")
    static CCharPointer BFGetError(IsolateThread t) {
        return toCString(lastError).get();
    }

    @CEntryPoint(name = "bf_open")
    static byte BFOpen(IsolateThread t, final CCharPointer filePath) {
        try {
            // Use the easier resolution API
            reader.setFlattenedResolutions(false);
            reader.setId(toJavaString(filePath));
            reader.setMetadataStore(metadata);
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

    @CEntryPoint(name = "bf_get_used_files")
    // Returns null-separated files. If two nulls are consecutive, then this means
    // end of list
    static CCharPointer BFGetUsedFiles(IsolateThread t) {
        try {
            String[] files = reader.getUsedFiles();
            int charI = 0;
            for (String file : files) {
                char[] characters = file.toCharArray();
                if (characters.length + 2 > communicationBuffer.length) {
                    lastError = "Too long";
                    return WordFactory.nullPointer();
                }
                for (int i = 0; i < characters.length; i++) {
                    communicationBuffer[charI++] = characters[i];
                }
                communicationBuffer[charI++] = 0;
            }
            for (; charI < communicationBuffer.length; charI++) {
                communicationBuffer[charI] = 0;
            }
            return toCBytes(communicationBuffer).get();
        } catch (Exception e) {
            lastError = e.toString();
            return WordFactory.nullPointer();
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

    @CEntryPoint(name = "bf_set_current_resolution")
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

    @CEntryPoint(name = "bf_get_optimal_tile_width")
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
            return WordFactory.nullPointer();
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
        // https://downloads.openmicroscopy.org/bio-formats/latest/api/loci/formats/IFormatReader.html#getPixelType--
        // https://github.com/ome/bioformats/blob/9cb6cfaaa5361bcc4ed9f9841f2a4caa29aad6c7/components/formats-api/src/loci/formats/FormatTools.java#L96
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

    @CEntryPoint(name = "bf_floating_point_is_normalized")
    static byte BFIsNormalized(IsolateThread t) {
        // tells whether to normalize floating point data
        try {
            return toCBoolean(reader.isNormalized());
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
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(name = "bf_is_order_certain")
    static byte BFIsOrderCertain(IsolateThread t) {
        try {
            return toCBoolean(reader.isOrderCertain());
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    // TODO: verify that returning null pointer is OKAY
    // TODO: return a 10MB array 100 times and see if there are memory leaks
    @CEntryPoint(name = "bf_open_bytes")
    static CCharPointer BFOpenBytes(IsolateThread t, int x, int y, int w, int h) {
        try {
            int size = w * h * FormatTools.getBytesPerPixel(reader.getPixelType()) * reader.getRGBChannelCount();
            if (size > communicationBuffer.length) {
                lastError = "Requested tile too big; must be at most " + communicationBuffer.length + " bytes";
                return WordFactory.nullPointer();
            }
            // TODO: for example for noninterleaved channels, we'll need to handle other
            // planes.
            // To implement that one would need to read the description of
            // https://downloads.openmicroscopy.org/bio-formats/5.4.1/api/loci/formats/IFormatReader.html#getEffectiveSizeC--
            // and understand the difference between getimagecount and getseriescount
            reader.openBytes(0, communicationBuffer, x, y, w, h);
            // Erase previous responses
            for (int i = size; i < communicationBuffer.length; i++) {
                communicationBuffer[i] = 0;
            }
            // This likely does not copy
            // https://github.com/oracle/graal/blob/492c6016c5d9233be5de2dd9502cc81f746fc8e7/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/c/CTypeConversionSupportImpl.java#L206
            return toCBytes(communicationBuffer).get();
        } catch (Exception e) {
            lastError = e.toString();
            // This is permitted:
            // https://github.com/oracle/graal/blob/492c6016c5d9233be5de2dd9502cc81f746fc8e7/substratevm/src/com.oracle.svm.core/src/com/oracle/svm/core/c/CTypeConversionSupportImpl.java#L55
            return WordFactory.nullPointer();
        }
    }

    // An alternative to openBytes is openPlane
    // https://downloads.openmicroscopy.org/bio-formats/latest/api/loci/formats/IFormatReader.html#openPlane-int-int-int-int-int-
    // some types are
    // https://github.com/search?q=repo%3Aome%2Fbioformats+getNativeDataType&type=code

    // https://bio-formats.readthedocs.io/en/latest/metadata-summary.html
    @CEntryPoint(name = "bf_get_mpp_x")
    // 0 if not defined, -1 for error
    static double BFGetMPPX(IsolateThread t) {
        try {
            // TODO: modify to handle multiple series
            var size = metadata.getPixelsPhysicalSizeX(0);
            if (size == null) {
                return 0d;
            }
            return size.value(UNITS.MICROMETER).doubleValue() / reader.getSizeX();
        } catch (Exception e) {
            lastError = e.toString();
            return -1d;
        }

    }

    @CEntryPoint(name = "bf_get_mpp_y")
    static double BFGetMPPY(IsolateThread t) {
        try {
            // TODO: modify to handle multiple series
            var size = metadata.getPixelsPhysicalSizeY(0);
            if (size == null) {
                return 0d;
            }
            return size.value(UNITS.MICROMETER).doubleValue() / reader.getSizeY();
        } catch (Exception e) {
            lastError = e.toString();
            return -1d;
        }

    }

    @CEntryPoint(name = "bf_get_mpp_z")
    static double BFGetMPPZ(IsolateThread t) {
        try {
            // TODO: modify to handle multiple series
            var size = metadata.getPixelsPhysicalSizeZ(0);
            if (size == null) {
                return 0d;
            }
            return size.value(UNITS.MICROMETER).doubleValue() / reader.getSizeZ();
        } catch (Exception e) {
            lastError = e.toString();
            return -1d;
        }
    }

    @CEntryPoint(name = "bf_get_current_file")
    static CCharPointer BFGetCurrentFile(IsolateThread t) {
        try {
            String file = reader.getCurrentFile();
            if (file == null) {
                return WordFactory.nullPointer();
            } else {
                return toCString(file).get();
            }
        } catch (Exception e) {
            lastError = e.toString();
            return WordFactory.nullPointer();
        }
    }

    @CEntryPoint(name = "bf_is_any_file_open")
    static byte BFIsAnyFileOpen(IsolateThread t) {
        try {
            return toCBoolean(reader.getCurrentFile() == null);
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bftools_should_generate")
    // Once a file is successfully opened, call this to see if we need to
    // regenerate the pyramid
    static byte BFToolsShouldGenerate(IsolateThread t) {
        try {
            int levels = reader.getResolutionCount();
            int previousX = 0;
            int previousY = 0;
            boolean shouldGenerate = false;
            for (int i = 0; i < levels; i++) {
                reader.setResolution(i);
                int x = reader.getSizeX();
                int y = reader.getSizeY();
                // I chose 4 to allow some margin
                // the strict alternative would bea bigger divisor or
                // "> 2*x+1"
                if (previousX > 2 * x + x / 4
                        || previousY > 2 * y + y / 4) {
                    shouldGenerate = true;
                    break;
                }
                previousX = x;
                previousY = y;
            }
            if (previousX > 320 && previousY > 320) {
                shouldGenerate = true;
            }
            return toCBoolean(shouldGenerate);
        } catch (Exception e) {
            lastError = e.toString();
            return -1;
        }
    }

    @CEntryPoint(name = "bftools_generate_subresolutions")
    // Outpath must have .ome.tiff extension
    // tiff or ome.tiff extensions can contain subresolutions
    // but bfconvert can sometimes produce results incompatible with others
    // https://forum.image.sc/t/bfconvert-breaks-images-with-alpha-layer-wrong-interleave/83482
    // so reserve ome.tiff for bioformats-reading
    static byte BFToolsGenerateSubresolutions(IsolateThread t, CCharPointer inPathPtr, CCharPointer outPathPtr,
            int layers) {
        // https://bio-formats.readthedocs.io/en/latest/developers/wsi.html#pyramids-in-ome-tiff
        // https://bio-formats.readthedocs.io/en/v6.14.0/users/comlinetools/conversion.html
        // https://bio-formats.readthedocs.io/en/v6.14.0/users/comlinetools/conversion.html#cmdoption-bfconvert-pyramid-scale
        // "jar tvf" on bioformats-tools.jar shows classes
        // Meta-inf says main is in loci.formats.tools.ImageInfo
        // But there are multiple entry points.
        try {
            String inPath = toJavaString(inPathPtr);
            String outPath = toJavaString(outPathPtr);

            ImageConverter.main(new String[] { "-noflat", "-pyramid-resolutions", Integer.toString(layers),
                    "-pyramid-scale", "2", inPath, outPath });
            reader.setId(outPath);
            return toCBoolean(true);
        } catch (Exception e) {
            lastError = e.toString();
            return toCBoolean(false);
        }
    }

    // Debug function
    public static byte openFile(String filename) throws Exception {
        try {
            System.out.println("Step 1");

            ImageInputStream stream = new MemoryCacheImageInputStream(new BufferedInputStream(new FileInputStream(
                    "/Users/zerf/Desktop/Screenshot 2023-06-30 at 15.31.08.png"), 81920));
            var b = ImageIO.read(stream);

            System.out.println("start 1");
            reader.close(true);
            System.out.println("start 2.5?");

            reader.setId("/Users/zerf/Downloads/Github-repos/CGDogan/camic-Distro/images/posdebugfiles_2.dcm");
            System.out.println(reader.getRGBChannelCount());

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
