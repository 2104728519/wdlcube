/**
 * min2phaseCXX Copyright (C) 2022 Borgo Federico
 * This program comes with ABSOLUTELY NO WARRANTY; for details type `show w'.
 * This is free software, and you are welcome to redistribute it
 * under certain conditions; type `show c' for details.
 */

#ifndef MIN2PHASE_SEARCH_H
#define MIN2PHASE_SEARCH_H 1

#include "coords.h"
#include <thread>
#include <vector>
#include <atomic>
#include <mutex>
#include <chrono>

namespace min2phase {

    class SearchCallback; // 前向声明

    // 多线程并发共享上下文
    struct ParallelContext {
        std::atomic<bool> aborted{false};
        std::mutex resultMutex;
        std::string bestSolution = "";
        int8_t bestSolLen = 127;
        std::atomic<int64_t> totalNodes{0};

        // 高精度首解时间记录与解法存在标记
        std::chrono::high_resolution_clock::time_point firstSolTime;
        std::atomic<bool> hasSolution{false};
    };

    class Search {
    private:

        static const int8_t MAX_PRE_MOVES = 20;
        static const int8_t MIN_P1LENGTH_PRE = 7;

        coords::CoordCube nodeUD[MAX_PRE_MOVES + 1]{};
        coords::CoordCube nodeRL[MAX_PRE_MOVES + 1]{};
        coords::CoordCube nodeFB[MAX_PRE_MOVES + 1]{};

        CubieCube urfCubieCube[info::N_BASIC_MOVES];
        coords::CoordCube urfCoordCube[info::N_BASIC_MOVES]{};
        CubieCube phase1Cubie[MAX_PRE_MOVES + 1];
        CubieCube preMoveCubes[MAX_PRE_MOVES + 1];
        CubieCube solveCube;
        CubieCube::OutputFormat solution;

        int8_t move[info::MAX_LENGTH] = {0};
        int8_t preMoves[MAX_PRE_MOVES] = {0};

        uint8_t* movesUsed = nullptr;

        int64_t selfSym = 0;
        int8_t conjMask = 0;
        int8_t urfIdx = 0;
        int8_t length1 = 0;
        int8_t depth1 = 0;
        int8_t maxDep2 = 0;
        int8_t solLen = 0;
        int32_t probe = 0;
        int32_t probeMax = 0;
        int32_t probeMin = 0;
        int8_t verbose = 0;
        int8_t valid1 = 0;
        int8_t preMoveLen = 0;
        int8_t maxPreMoves = 0;

        bool allowShorter = false;

        // 用于回调与中断控制的私有属性
        SearchCallback* m_callback = nullptr;
        ParallelContext* m_parallelCtx = nullptr; // 多线程上下文指针
        int32_t checkCountdown = 1000000;
        int64_t totalNodes = 0;
        bool isAborted = false;

        // 高频状态轮询检测助手
        bool checkCallback(int8_t depth);

    public:

        Search() = default;

        /**
         * This is used to solve the cube.
         */
        std::string solve(const std::string &facelets, int8_t maxDepth, int32_t probeMax, int32_t probeMin,
                          int8_t verbose, uint8_t *movesUsed, SearchCallback* callback = nullptr);

        // 供子线程调用的单轴并行搜索接口
        std::string solveSingleAxis(const std::string &facelets, int8_t urf, int8_t maxDepth, int32_t probeMax, int32_t probeMin,
                                    int8_t verbose, ParallelContext* parallelCtx, SearchCallback* callback);

        int8_t verify(const std::string &facelets);

    private:
        void initSearch();

        std::string search();

        int8_t phase1PreMoves(int8_t maxl, int8_t lm, CubieCube *cc, uint16_t ssym);

        int8_t phase1(coords::CoordCube *node, uint16_t ssym, int8_t maxl, int8_t lm);

        int8_t initPhase2Pre();

        int8_t initPhase2(uint16_t p2corn, int8_t p2csym, uint16_t p2edge, int8_t p2esym, int8_t p2mid, uint16_t edgei,
                          uint16_t corni);

        int8_t phase2(uint16_t edge, int8_t esym, uint16_t corn, int8_t csym, int8_t mid, int8_t maxl, int8_t depth,
                      int8_t lm);

        std::string searchOpt();

        int8_t phase1opt(coords::CoordCube ud, coords::CoordCube rl, coords::CoordCube fb, int64_t ssym, int8_t maxl, int8_t lm);
    };
}

#endif //MIN2PHASE_SEARCH_H