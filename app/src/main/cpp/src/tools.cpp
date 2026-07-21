/**
 * min2phaseCXX Copyright (C) 2022 Borgo Federico
 * This program comes with ABSOLUTELY NO WARRANTY; for details type `show w'.
 * This is free software, and you are welcome to redistribute it
 * under certain conditions; type `show c' for details.
 */

#include <min2phase/tools.h>
#include <memory>
#include <chrono>
#include <iostream>
#include <fstream>
#include <iomanip>
#include <min2phase/min2phase.h>
#include "coords.h"
#include "Search.h"

namespace min2phase { namespace tools {

#ifdef __linux__
#define FILE_PATH "/tmp/cpu_model_cube.txt"
#elif defined(_WIN32)
#define FILE_PATH "cpu_model_cube.txt"
#endif

        static const int8_t N_INIT_BENCH = 10;
        static const int8_t N_SOL_BENCH = 20;
        static const int16_t MIN_PROBES_LIMIT = 10240;

        void setRandomSeed(uint32_t seed) {
            std::srand(seed);
        }

        std::string randomCube() {
            if (!coords::isInit())
                return "";

            CubieCube cube;

            int8_t parity;
            int16_t cornerOri, edgeOri;
            uint16_t cornerPerm;
            int32_t edgePerm;

            cornerPerm = std::rand() % (info::N_PERM + 1);
            cornerOri = std::rand() % (info::N_TWIST + 1);
            edgeOri = std::rand() % (info::N_FLIP + 1);
            parity = CubieCube::getNParity(cornerPerm, info::NUMBER_CORNER);

            do {
                edgePerm = std::rand() % (info::FULL_E_PERM + 1);
            } while (CubieCube::getNParity(edgePerm, info::NUMBER_EDGES) != parity);

            cube.setCoords(cornerPerm, cornerOri, edgePerm, edgeOri);

            return CubieCube::toFaceCube(cube);
        }

        std::string fromScramble(const int8_t scramble[], uint8_t length) {
            uint8_t i;
            CubieCube c1;
            CubieCube c2;
            CubieCube tmp;

            for (i = 0; i < length; i++) {
                CubieCube::cornMult(c1, coords::coords.moveCube[scramble[i]], c2);
                CubieCube::edgeMult(c1, coords::coords.moveCube[scramble[i]], c2);
                tmp = c1;
                c1 = c2;
                c2 = tmp;
            }

            return CubieCube::toFaceCube(c1);
        }

        std::string fromScramble(const std::string &s) {
            std::unique_ptr<int8_t> arr(new int8_t[s.length()]);

            int8_t n_moves, axis;

            n_moves = 0;
            axis = -1;

            for (size_t i = 0; i < s.length(); i++) {
                arr.get()[i] = 0;

                switch (s[i]) {
                    case 'U':
                        axis = 0;
                        break;
                    case 'R':
                        axis = 3;
                        break;
                    case 'F':
                        axis = 6;
                        break;
                    case 'D':
                        axis = 9;
                        break;
                    case 'L':
                        axis = 12;
                        break;
                    case 'B':
                        axis = 15;
                        break;
                    case ' ':
                        if (axis != -1)
                            arr.get()[n_moves++] = axis;
                        axis = -1;
                        break;
                    case '2':
                        axis++;
                        break;
                    case '\'':
                        axis += 2;
                        break;
                    default:
                        break;
                }
            }

            if (axis != -1)
                arr.get()[n_moves++] = axis;

            return fromScramble(arr.get(), n_moves);
        }

        std::string superFlip(){
            return CubieCube::toFaceCube(CubieCube(0, 0, 0, info::N_FLIP-1));
        }

        /**
         * Benchmark the initialization of coordinates.
         *
         * @return      : the average time of initialization.
         */
        static unsigned long benchInit() {
            using namespace std::chrono;

            unsigned long time_elapsed = 0;
            time_point<high_resolution_clock> begin, end;
            int i;

            for (i = 0; i < N_INIT_BENCH; i++) {
                begin = high_resolution_clock::now();
                init();
                end = high_resolution_clock::now();

                time_elapsed += duration_cast<milliseconds>(end - begin).count();
            }

            return time_elapsed / N_INIT_BENCH;
        }

        /**
         * Benchmark the search for a solution.
         *
         * @param avgMove       : where to store the average move used.
         * @param probeMin      : min probes to use for the search.
         * @param avgTime       : where to store the average time for a search.
         * @param maxMoves      : max moves to solve the cube.
         */
        static void benchSearch(float *avgMove, int32_t probeMin, float *avgTime, int8_t maxMoves) {
            using namespace std::chrono;

            std::string randState;
            time_point<high_resolution_clock> begin, end;
            uint8_t usedMoves;
            long movesCount = 0;
            int i;

            *avgTime = 0;

            for (i = 0; i < N_SOL_BENCH; i++) {
                randState = min2phase::tools::randomCube();
                begin = high_resolution_clock::now();
                solve(randState, maxMoves, 1000000, probeMin, 0, &usedMoves);
                end = high_resolution_clock::now();

                *avgTime += (float) duration_cast<milliseconds>(end - begin).count();
                movesCount += usedMoves;
            }

            *avgTime /= N_SOL_BENCH;

            *avgMove = (float) movesCount / N_SOL_BENCH;
        }

        /**
         * Android 环境下安全地获取设备 CPU 信息
         */
        static std::string getCPUname() {
#if defined(__ANDROID__)
            // 在 Android 环境中避免调用 system 命令导致崩溃，直接返回通用架构信息
#if defined(__arm__)
            return "ARMv7 CPU (Android)";
#elif defined(__aarch64__)
            return "ARM64 CPU (Android)";
#elif defined(__i386__) || defined(__x86_64__)
            return "x86 CPU (Android)";
#else
            return "Generic Mobile CPU (Android)";
#endif
#else
            // 以下为原项目桌面端的获取逻辑（非 Android 时使用）
            std::string cpuName;
            std::ifstream cpuNameFile;
            #ifdef __linux__
                system(R"(cat /proc/cpuinfo | grep "model name" | cut -f2 -d":" | cut -c2- > )" FILE_PATH);
                cpuNameFile.open(FILE_PATH);
                getline(cpuNameFile, cpuName);
                cpuNameFile.close();
                system("rm " FILE_PATH);
                cpuName += " (OS Linux)";
            #elif defined(_WIN32)
                system("wmic cpu get name > " FILE_PATH);
                cpuNameFile.open(FILE_PATH);
                getline(cpuNameFile, cpuName);
                getline(cpuNameFile, cpuName);
                cpuNameFile.close();
                system("del " FILE_PATH);
                cpuName += " (OS Windows)";
            #else
                cpuName = "undefined";
            #endif
            return cpuName;
#endif
        }

        void benchmark() {
            using namespace std;
            float avgTime, avgMoves;

            cout << "CPU model: " << getCPUname() << endl;

            min2phase::tools::setRandomSeed(time(nullptr));

            cout << "Init time average: " << benchInit() << "ms\n\n";

            cout << "| probeMin | Avg Length |   Time   |\n|:--------:|:----------:|:--------:|\n";
            for (int32_t probe = 5; probe <= MIN_PROBES_LIMIT; probe *= 2) {
                benchSearch(&avgMoves, probe, &avgTime, 31);

                cout << "|" << fixed << setw(7) << probe << "   |";
                cout << fixed << setprecision(1) << setw(8) << avgMoves << "    |";
                cout << fixed << setprecision(1) << setw(6) << avgTime << " ms |\n";
            }

            cout << "\n|   Time    |  Max Moves |\n|:---------:|:----------:|\n";
            for (int8_t maxDepth = 31; maxDepth >= 20; maxDepth--) {
                benchSearch(&avgMoves, 0, &avgTime, maxDepth);

                cout << "|" << fixed << setprecision(1) << setw(5) << avgTime << " ms   |";
                cout << fixed << setprecision(1) << setw(7) << (int32_t) maxDepth << "     |\n";
            }
        }

        int8_t verify(const std::string& facelets){
            Search s;

            return s.verify(facelets);
        }


        void testAlgorithm(){
            // min2phase::init();
        }
    } }
