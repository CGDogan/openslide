FROM ubuntu:focal

### update
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get -q update
RUN apt-get -q -y upgrade
RUN apt-get -q -y dist-upgrade
RUN apt-get clean
RUN apt-get -q update

RUN apt-get -q -y install g++ git cmake autoconf automake libtool pkg-config
RUN apt-get -q -y install zlib1g-dev libpng-dev libjpeg-dev libtiff5-dev libgdk-pixbuf2.0-dev libxml2-dev libsqlite3-dev libcairo2-dev libglib2.0-dev

RUN mkdir /root/src
COPY . /root/src
WORKDIR /root/src

### openjpeg version in ubuntu 14.04 is 1.3, too old and does not have openslide required chroma subsampled images support.  download 2.3.0 from source and build
RUN git clone https://github.com/uclouvain/openjpeg.git --branch=v2.3.0
RUN mkdir /root/src/openjpeg/build
WORKDIR /root/src/openjpeg/build
RUN cmake -DBUILD_JPIP=ON -DBUILD_SHARED_LIBS=ON -DCMAKE_BUILD_TYPE=Release -DBUILD_CODEC=ON -DBUILD_PKGCONFIG_FILES=ON ../
RUN make
RUN make install

### Openslide
WORKDIR /root/src
## get my fork from openslide source code
RUN git clone https://github.com/openslide/openslide.git

## build openslide
WORKDIR /root/src/openslide
RUN git checkout tags/v3.4.1
RUN autoreconf -i
#RUN ./configure --enable-static --enable-shared=no
# may need to set OPENJPEG_CFLAGS='-I/usr/local/include' and OPENJPEG_LIBS='-L/usr/local/lib -lopenjp2'
# and the corresponding TIFF flags and libs to where bigtiff lib is installed.
RUN ./configure
RUN make
RUN make install
