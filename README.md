# caMicroscope OpenSlide Docker image

Used by [SlideLoader](https://github.com/camicroscope/slideloader) for serving image metadata and by [iipimage](https://github.com/camicroscope/iipImage) for serving pixels.

### Dummy apt packages

After compiling and `make install`ing OpenSlide, packages such as libvips will try to install alternative OpenSlides such as libopenslide0 from apt. A safe way to solve this is to create and install dummy packages, as suggested on StackExchange ([1](https://askubuntu.com/q/74523), and [2](https://serverfault.com/a/251091)). Something to watch out for is that the dummy package must have a version that satisfies the requirements of dependendees. Currently the strictest dependees had the requirement: ">=3.4.1+..." and this was released in 2015. Therefore the current version in this repository is 3.4.2 but feel free to update it to 4.0.0 since no library I could find currently checks for "<4.0.0"

To check that an OpenSlide installation is recent enough and supports DICOM:

```BASH
wget https://medistim.com/wp-content/uploads/2016/07/ttfm.dcm
python3 -c "o={};exec(\"import openslide;\nif openslide.OpenSlide.detect_format('ttfm.dcm') is None:\n\tres='old openslide'\nelse:\n\tres='new openslide'\n\",globals(),o);print(o['res'])"
```

Which will print the result or give an error if OpenSlide could not be found.

A second version returns a nonzero process exit code if a DICOM file cannot not be read. This is useful for Dockerfiles:

```DOCKER
RUN wget https://medistim.com/wp-content/uploads/2016/07/ttfm.dcm
RUN python3 -c "o={};exec(\"import openslide;\nif openslide.OpenSlide.detect_format('ttfm.dcm') is not None:\n\tres='new openslide'\n\",globals(),o);print(o['res'])"
```
