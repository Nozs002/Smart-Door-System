FROM alpine:3.22
RUN apk add --no-cache build-base
WORKDIR /src
COPY node-auth/PinInput.h firmware/node-auth/PinInput.h
COPY node-auth/src/bcrypt/ firmware/node-auth/src/bcrypt/
COPY tests/native/input_tests.cpp firmware/tests/native/input_tests.cpp
RUN gcc -Wall -Wextra -c firmware/node-auth/src/bcrypt/crypt_blowfish.c -o /tmp/bcrypt.o \
    && g++ -std=c++17 -Wall -Wextra -Werror firmware/tests/native/input_tests.cpp /tmp/bcrypt.o -o /tmp/input-tests
CMD ["/tmp/input-tests"]
