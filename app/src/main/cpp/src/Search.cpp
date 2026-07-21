/**
 * min2phaseCXX Copyright (C) 2022 Borgo Federico
 * This program comes with ABSOLUTELY NO WARRANTY; for details type `show w'.
 * This is free software, and you are welcome to redistribute it
 * under certain conditions; type `show c' for details.
 */

#include <min2phase/min2phase.h>
#include "Search.h"

// 创建多个子线程并行搜索不同的对称轴
std::string min2phase::Search::solve(const std::string &facelets, int8_t maxDepth, int32_t probeMax, int32_t probeMin,
                                     int8_t verbose, uint8_t *movesUsed, SearchCallback* callback) {
    if (!coords::isInit())
        return std::to_string(info::MISSING_COORDS);

    int8_t error = verify(facelets);
    if (error != 0)
        return std::to_string((int32_t)error);

    MIN2PHASE_OUTPUT("Multi-threaded coordination initialized.")

    // 共享上下文，用于各核心间同步计算状态
    ParallelContext parallelCtx;
    parallelCtx.bestSolLen = maxDepth + 1;

    std::vector<std::thread> threads;

    if ((verbose & OPTIMAL_SOLUTION) == 0) {
        // 双阶段常规求解（或持续压榨）：最多并行运行 6 个对称轴线程
        for (int8_t urf = 0; urf < 6; urf++) {
            threads.push_back(std::thread([&, urf]() {
                Search axisSolver;
                axisSolver.solveSingleAxis(facelets, urf, maxDepth, probeMax, probeMin, verbose, &parallelCtx, callback);
            }));
        }
    } else {
        // 最优解求解：只需并行运行 0 号轴和 3 号轴对应的三个面搜索
        for (int8_t urf : {0, 3}) {
            threads.push_back(std::thread([&, urf]() {
                Search axisSolver;
                axisSolver.solveSingleAxis(facelets, urf, maxDepth, probeMax, probeMin, verbose, &parallelCtx, callback);
            }));
        }
    }

    for (auto &t : threads) {
        if (t.joinable()) {
            t.join();
        }
    }

    if (parallelCtx.bestSolution.empty()) {
        return std::to_string(info::SHORT_DEPTH);
    }

    if (movesUsed != nullptr) {
        *movesUsed = parallelCtx.bestSolLen;
    }

    return parallelCtx.bestSolution;
}

std::string min2phase::Search::solveSingleAxis(const std::string &facelets, int8_t urf, int8_t maxDepth, int32_t probeMax, int32_t probeMin,
                                               int8_t verbose, ParallelContext* parallelCtx, SearchCallback* callback) {
    m_callback = callback;
    m_parallelCtx = parallelCtx;
    isAborted = false;
    totalNodes = 0;
    checkCountdown = 1000000;

    // 填充 solveCube 数据结构，上层已验证过有效性
    verify(facelets);

    this->solLen = maxDepth + 1;
    this->probe = 0;
    this->probeMax = probeMax;
    this->probeMin = probeMin;
    this->verbose = verbose;

    initSearch();

    this->urfIdx = urf;

    if ((verbose & OPTIMAL_SOLUTION) == 0) {
        for (length1 = 0; length1 < solLen; length1++) {
            if (isAborted || (m_parallelCtx != nullptr && m_parallelCtx->aborted.load())) {
                break;
            }
            maxDep2 = std::min((int32_t)info::P1_LENGTH, int32_t(solLen - length1 - 1));

            if ((conjMask & 1 << urfIdx) != 0) {
                break;
            }

            if (phase1PreMoves(maxPreMoves, -30, &urfCubieCube[urfIdx], (int32_t)(selfSym & 0xffff)) == 0) {
                break;
            }
        }
    } else {
        if (urf == 0 || urf == 3) {
            phase1Cubie[0] = urfCubieCube[urfIdx];
            for (length1 = 0; length1 < solLen; length1++) {
                if (isAborted || (m_parallelCtx != nullptr && m_parallelCtx->aborted.load())) {
                    break;
                }
                coords::CoordCube ud = urfCoordCube[0 + urfIdx];
                coords::CoordCube rl = urfCoordCube[1 + urfIdx];
                coords::CoordCube fb = urfCoordCube[2 + urfIdx];

                if (ud.prun <= length1 && rl.prun <= length1 && fb.prun <= length1
                    && phase1opt(ud, rl, fb, selfSym, length1, -1) == 0) {
                    break;
                }
            }
        }
    }

    return "";
}

int8_t min2phase::Search::verify(const std::string& facelets) {
    char centers[info::FACES];
    int8_t cube [info::N_PLATES];
    int32_t count = 0x000000;
    int8_t i, j;
    bool notQuit;

    if(facelets.length() != info::N_PLATES)
        return info::MALFORMED_STRING;

    centers[0] = facelets[info::U5];
    centers[1] = facelets[info::R5];
    centers[2] = facelets[info::F5];
    centers[3] = facelets[info::D5];
    centers[4] = facelets[info::L5];
    centers[5] = facelets[info::B5];


    for (i = 0; i < info::N_PLATES; i++) {
        cube[i] = -1;
        notQuit = true;

        for(j = 0; notQuit && j < info::FACES; j++){
            if(centers[j] == facelets[i]){
                notQuit = false;
                cube[i] = j;
            }
        }

        if (cube[i] == -1)
            return info::MALFORMED_STRING;

        count += 1 << (cube[i] << 2);
    }

    if (count != 0x999999)
        return info::MALFORMED_STRING;

    CubieCube::toCubieCube(cube, solveCube);

    MIN2PHASE_OUTPUT("Solving a cube.")

    return solveCube.check();
}

void min2phase::Search::initSearch() {
    int8_t i;
    selfSym = solveCube.selfSym();
    conjMask |= (selfSym >> 16 & 0xffff) != 0 ? 0x12 : 0;
    conjMask |= (selfSym >> 32 & 0xffff) != 0 ? 0x24 : 0;
    conjMask |= (selfSym >> 48 & 0xffff) != 0 ? 0x38 : 0;
    selfSym &= 0xffffffffffffL;
    maxPreMoves = conjMask > 7 ? 0 : MAX_PRE_MOVES;

    for (i = 0; i < info::N_BASIC_MOVES; i++) {
        urfCubieCube[i].copy(solveCube);
        urfCoordCube[i].setWithPrun(urfCubieCube[i], 20);
        solveCube.URFConjugate();

        if (i%3 == 2)
            solveCube.inv();
    }
}

// 供单核回退时调用的入口，多核并行不走此流程
std::string min2phase::Search::search() {
    for (length1 = 0; length1 < solLen; length1++) {
        if (isAborted) return solution.isFound ? solution.toString() : "Error 8";
        maxDep2 = std::min((int32_t)info::P1_LENGTH, int32_t(solLen - length1 - 1));

        for (urfIdx =  0; urfIdx < info::N_BASIC_MOVES; urfIdx++) {

            if ((conjMask & 1 << urfIdx) != 0)
                continue;

            if (phase1PreMoves(maxPreMoves, -30, &urfCubieCube[urfIdx], (int32_t)(selfSym & 0xffff)) == 0){
                if (isAborted) return solution.isFound ? solution.toString() : "Error 8";
                if(!solution.isFound)
                    return std::to_string(info::PROBE_LIMIT);

                if(movesUsed != nullptr)
                    *movesUsed = solLen;

                return solution.toString();
            }
        }
    }

    if(!solution.isFound)
        return std::to_string(info::SHORT_DEPTH);

    if(movesUsed != nullptr)
        *movesUsed = solLen;

    return solution.toString();
}

int8_t min2phase::Search::phase1PreMoves(int8_t maxl, int8_t lm, CubieCube* cc, uint16_t ssym) {
    if (checkCallback(length1)) return 1;
    int8_t m, ret;
    int32_t skipMoves;

    const int32_t VAL = 0x36FB7;

#if MIN2PHASE_DEBUG > 1
    MIN2PHASE_OUTPUT("Phase 1 pre moves.")
#endif

    preMoveLen = maxPreMoves-maxl;

    if (preMoveLen == 0 || ((VAL >> (lm+32)) & 1) == 0) {
        depth1 = length1-preMoveLen;
        phase1Cubie[0] = *cc;
        allowShorter = depth1 == MIN_P1LENGTH_PRE && preMoveLen != 0;

        if (nodeUD[depth1+1].setWithPrun(*cc, depth1)
            && phase1(&nodeUD[depth1+1], ssym, depth1, -1) == 0)
            return 0;
    }

    if (maxl == 0 || preMoveLen+MIN_P1LENGTH_PRE >= length1)
        return 1;

    if (maxl == 1 || preMoveLen+1+MIN_P1LENGTH_PRE >= length1)
        skipMoves = VAL;
    else
        skipMoves = 0;

    lm = lm / 3 * 3;

    for (m = 0; m < info::P2_LENGTH; m++) {
        if (m == lm || m == lm - 9 || m == lm + 9) {
            m += 2;
            continue;
        }

        if ((skipMoves & 1 << m) != 0)
            continue;

        CubieCube::cornMult(coords::coords.moveCube[m], *cc, preMoveCubes[maxl]);
        CubieCube::edgeMult(coords::coords.moveCube[m], *cc, preMoveCubes[maxl]);
        preMoves[maxPreMoves - maxl] = m;
        ret = phase1PreMoves(maxl - 1, m, &preMoveCubes[maxl], ssym & (int32_t) coords::coords.moveCubeSym[m]);

        if (ret == 0)
            return 0;
    }

    return 1;
}

int8_t min2phase::Search::phase1(coords::CoordCube* node, uint16_t ssym, int8_t maxl, int8_t lm) {
    if (checkCallback(depth1)) return 0;
    int8_t ret, axis, m, power, prun;

#if MIN2PHASE_DEBUG > 1
    MIN2PHASE_OUTPUT("Phase 1.")
#endif

    if (node->prun == 0 && maxl < 5) {
        if (allowShorter || maxl == 0) {
            depth1 -= maxl;
            ret = initPhase2Pre();
            depth1 += maxl;
            return ret;
        }else
            return 1;
    }

    for (axis = 0; axis < info::P2_LENGTH; axis += 3) {
        if (axis == lm || axis == lm - 9)
            continue;

        for (power = 0; power < 3; power++) {
            m = axis + power;

            prun = nodeUD[maxl].doMovePrun(*node, m);

            if (prun > maxl)
                break;
            else if (prun == maxl)
                continue;

            prun = nodeUD[maxl].doMovePrunConj(*node, m);
            if (prun > maxl)
                break;
            else if (prun == maxl)
                continue;

            move[depth1 - maxl] = m;
            valid1 = std::min(valid1, int8_t(depth1 - maxl));
            ret = phase1(&nodeUD[maxl], ssym & (int32_t) coords::coords.moveCubeSym[m], maxl - 1, axis);

            if (ret == 0)
                return 0;
            else if (ret >= 2)
                break;
        }
    }

    return 1;
}

int8_t min2phase::Search::initPhase2Pre() {
    uint16_t p2corn, p2edge, edgei, corni;
    int8_t  p2csym, p2esym, p2mid, lastMove, lastPre, p2switchMax, p2switchMask;
    int8_t p2switch, ret;
    int8_t i, m;

#if MIN2PHASE_DEBUG > 1
    MIN2PHASE_OUTPUT("Init phase 2.")
#endif

    if (probe >= (!solution.isFound ? probeMax : probeMin))
        return 0;

    ++probe;

    for (i = valid1; i < depth1; i++) {
        CubieCube::cornMult(phase1Cubie[i], coords::coords.moveCube[move[i]], phase1Cubie[i + 1]);
        CubieCube::edgeMult(phase1Cubie[i], coords::coords.moveCube[move[i]], phase1Cubie[i + 1]);
    }

    valid1 = depth1;

    p2corn = phase1Cubie[depth1].getCPermSym();
    p2csym = p2corn & 0xf;
    p2corn >>= 4;

    p2edge = phase1Cubie[depth1].getEPermSym();
    p2esym = p2edge & 0xf;
    p2edge >>= 4;

    p2mid = phase1Cubie[depth1].getMPerm();
    edgei = coords::getPermSymInv(p2edge, p2esym, false);
    corni = coords::getPermSymInv(p2corn, p2csym, true);

    lastMove = depth1 == 0 ? -1 : move[depth1 - 1];
    lastPre = preMoveLen == 0 ? -1 : preMoves[preMoveLen - 1];

    ret = 0;
    p2switchMax = (preMoveLen == 0 ? 1 : 2) * (depth1 == 0 ? 1 : 2);
    p2switchMask = (1 << p2switchMax)-1;

    for (p2switch = 0; p2switch < p2switchMax; p2switch++) {

        if ((p2switchMask >> p2switch & 1) != 0) {
            p2switchMask &= ~(1 << p2switch);
            ret = initPhase2(p2corn, p2csym, p2edge, p2esym, p2mid, edgei, corni);

            if (ret == 0 || ret > 2)
                break;
            else if (ret == 2)
                p2switchMask &= 0x4 << p2switch;
        }

        if (p2switchMask == 0)
            break;

        if ((p2switch & 1) == 0 && depth1 > 0) {
            m = info::std2ud[lastMove / 3 * 3 + 1];
            move[depth1 - 1] = info::ud2std[m] * 2 - move[depth1 - 1];

            p2mid = coords::coords.MPermMove[p2mid][m];
            p2corn = coords::coords.CPermMove[p2corn][coords::coords.SymMoveUD[p2csym][m]];
            p2csym = coords::coords.SymMult[p2corn & 0xf][p2csym];
            p2corn >>= 4;
            p2edge = coords::coords.EPermMove[p2edge][coords::coords.SymMoveUD[p2esym][m]];
            p2esym = coords::coords.SymMult[p2edge & 0xf][p2esym];
            p2edge >>= 4;
            corni = coords::getPermSymInv(p2corn, p2csym, true);
            edgei = coords::getPermSymInv(p2edge, p2esym, false);

        } else if (preMoveLen > 0) {
            m = info::std2ud[lastPre / 3 * 3 + 1];
            preMoves[preMoveLen - 1] = info::ud2std[m] * 2 - preMoves[preMoveLen - 1];

            p2mid = coords::coords.MPermInv[coords::coords.MPermMove[coords::coords.MPermInv[p2mid]][m]];
            p2corn = coords::coords.CPermMove[corni >> 4][coords::coords.SymMoveUD[corni & 0xf][m]];
            corni = p2corn & ~0xf | coords::coords.SymMult[p2corn & 0xf][corni & 0xf];
            p2corn = coords::getPermSymInv(corni >> 4, corni & 0xf, true);
            p2csym = p2corn & 0xf;
            p2corn >>= 4;
            p2edge = coords::coords.EPermMove[edgei >> 4][coords::coords.SymMoveUD[edgei & 0xf][m]];
            edgei = p2edge & ~0xf | coords::coords.SymMult[p2edge & 0xf][edgei & 0xf];
            p2edge = coords::getPermSymInv(edgei >> 4, edgei & 0xf, false);
            p2esym = p2edge & 0xf;
            p2edge >>= 4;
        }
    }

    if (depth1 > 0)
        move[depth1 - 1] = lastMove;

    if (preMoveLen > 0)
        preMoves[preMoveLen - 1] = lastPre;

    return ret == 0 ? 0 : 2;
}

int8_t min2phase::Search::initPhase2(uint16_t p2corn, int8_t p2csym, uint16_t p2edge, int8_t p2esym, int8_t p2mid, uint16_t edgei, uint16_t corni) {
    int8_t prun, depth2, i;
    int8_t ret;

#if MIN2PHASE_DEBUG > 1
    MIN2PHASE_OUTPUT("Init phase 2.")
#endif

    prun = std::max(
            coords::getPruning(coords::coords.EPermCCombPPrun,
                               (edgei >> 4) * info::N_COMB + coords::coords.CCombPConj[coords::coords.Perm2CombP[corni >> 4] & 0xff][coords::coords.SymMultInv[edgei & 0xf][corni & 0xf]]),
            std::max(
                    coords::getPruning(coords::coords.EPermCCombPPrun,
                                       p2edge * info::N_COMB + coords::coords.CCombPConj[coords::coords.Perm2CombP[p2corn] & 0xff][coords::coords.SymMultInv[p2esym][p2csym]]),
                    coords::getPruning(coords::coords.MCPermPrun,
                                       p2corn * info::N_MPERM + coords::coords.MPermConj[p2mid][p2csym])));

    if (prun > maxDep2)
        return prun - maxDep2;

    for (depth2 = maxDep2; depth2 >= prun; depth2--) {
        ret = phase2(p2edge, p2esym, p2corn, p2csym, p2mid, depth2, depth1, 10);

        if (ret < 0)
            break;

        depth2 -= ret;
        solLen = 0;

        solution.isFound = true;
        solution.reset();
        solution.setArgs(verbose, urfIdx, depth1);

        for (i = 0; i < depth1 + depth2; i++)
            solution.appendSolMove(move[i]);

        for (i = preMoveLen - 1; i >= 0; i--)
            solution.appendSolMove(preMoves[i]);

        solLen = solution.length;

        // 使用多线程共享互斥锁，将更优（更短）的解更新到全局上下文中
        if (m_parallelCtx != nullptr) {
            std::lock_guard<std::mutex> lock(m_parallelCtx->resultMutex);
            if (solLen < m_parallelCtx->bestSolLen) {
                m_parallelCtx->bestSolLen = solLen;
                m_parallelCtx->bestSolution = solution.toString();
                if (m_callback != nullptr) {
                    m_callback->onNewSolutionFound(m_parallelCtx->bestSolution, m_parallelCtx->bestSolLen);
                }

                // 常规非最优解搜索下的“黄金压榨控制”
                if ((verbose & OPTIMAL_SOLUTION) == 0) {
                    // 如果是持续压榨模式 (verbose & 0x10) 不为 0，则不设置时限，也不提前终止，只是不断更新 bestSolution 供外部收集
                    if ((verbose & 0x10) != 0) {
                        if (!m_parallelCtx->hasSolution.load()) {
                            m_parallelCtx->hasSolution.store(true);
                            m_parallelCtx->firstSolTime = std::chrono::high_resolution_clock::now();
                        }
                    } else {
                        // 正常的 150 毫秒黄金时限强退
                        if (solLen <= 18) {
                            m_parallelCtx->aborted.store(true);
                        } else {
                            if (!m_parallelCtx->hasSolution.load()) {
                                m_parallelCtx->hasSolution.store(true);
                                m_parallelCtx->firstSolTime = std::chrono::high_resolution_clock::now();
                            }
                        }
                    }
                }
            }
        } else {
            if (m_callback != nullptr) {
                m_callback->onNewSolutionFound(solution.toString(), solLen);
            }
        }
    }

    if (depth2 != maxDep2) {
        maxDep2 = std::min(int8_t (info::P2_LENGTH), int8_t (solLen - length1 - 1));
        return probe >= probeMin ? 0 : 1;
    }

    return 1;
}

int8_t min2phase::Search::phase2(uint16_t edge, int8_t esym, uint16_t corn, int8_t csym, int8_t mid, int8_t maxl, int8_t depth, int8_t lm) {
    if (checkCallback(depth)) return -1;
    uint16_t cornx, edgex, edgei, corni;
    int8_t midx, csymx, prun;
    int16_t moveMask;
    int8_t m, ret;

#if MIN2PHASE_DEBUG > 1
    MIN2PHASE_OUTPUT("Phase 2.")
#endif

    if (edge == 0 && corn == 0 && mid == 0)
        return maxl;

    moveMask = info::ckmv2bit[lm];

    for (m = 0; m < 10; m++) {

        if ((moveMask >> m & 1) != 0) {
            m += 0x42 >> m & 3;
            continue;
        }

        midx = coords::coords.MPermMove[mid][m];
        cornx = coords::coords.CPermMove[corn][coords::coords.SymMoveUD[csym][m]];
        csymx = coords::coords.SymMult[cornx & 0xf][csym];
        cornx >>= 4;
        edgex = coords::coords.EPermMove[edge][coords::coords.SymMoveUD[esym][m]];
        int8_t esymx = coords::coords.SymMult[edgex & 0xf][esym];
        edgex >>= 4;
        edgei = coords::getPermSymInv(edgex, esymx, false);
        corni = coords::getPermSymInv(cornx, csymx, true);

        prun = coords::getPruning(coords::coords.EPermCCombPPrun,
                                  (edgei >> 4) * info::N_COMB + coords::coords.CCombPConj[coords::coords.Perm2CombP[corni >> 4] & 0xff][coords::coords.SymMultInv[edgei & 0xf][corni & 0xf]]);
        if (prun > maxl + 1)
            return maxl - prun + 1;
        else if (prun >= maxl) {
            m += 0x42 >> m & 3 & (maxl - prun);
            continue;
        }

        prun = std::max(
                coords::getPruning(coords::coords.MCPermPrun,
                                   cornx * info::N_MPERM + coords::coords.MPermConj[midx][csymx]),
                coords::getPruning(coords::coords.EPermCCombPPrun,
                                   edgex * info::N_COMB + coords::coords.CCombPConj[coords::coords.Perm2CombP[cornx] & 0xff][coords::coords.SymMultInv[esymx][csymx]]));

        if(prun >= maxl) {
            m += 0x42 >> m & 3 & (maxl - prun);
            continue;
        }

        ret = phase2(edgex, esymx, cornx, csymx, midx, maxl - 1, depth + 1, m);

        if (ret >= 0) {
            move[depth] = info::ud2std[m];
            return ret;
        }

        if (ret < -2)
            break;

        if (ret < -1)
            m += 0x42 >> m & 3;
    }

    return -1;
}

// 供单核回退时调用的入口，多核并行不走此流程
std::string min2phase::Search::searchOpt() {
    int8_t maxprun1 = 0,  maxprun2 = 0;
    coords::CoordCube ud{};
    coords::CoordCube rl{};
    coords::CoordCube fb{};

    MIN2PHASE_OUTPUT("Optimal searching.")

    for (uint8_t i = 0; i < info::N_BASIC_MOVES; i++) {
        urfCoordCube[i].calcPrun(false);

        if (i < info::N_BASIC_MOVES/2)
            maxprun1 = std::max(maxprun1, urfCoordCube[i].prun);
        else
            maxprun2 = std::max(maxprun2, urfCoordCube[i].prun);
    }

    urfIdx = maxprun2 > maxprun1 ? 3 : 0;
    urfIdx = maxprun2 > maxprun1 ? 3 : 0;
    phase1Cubie[0] = urfCubieCube[urfIdx];

    for (length1 = 0; length1 < solLen; length1++) {
        if (isAborted) return solution.isFound ? solution.toString() : "Error 8";
        ud = urfCoordCube[0 + urfIdx];
        rl = urfCoordCube[1 + urfIdx];
        fb = urfCoordCube[2 + urfIdx];

        if (ud.prun <= length1 && rl.prun <= length1 && fb.prun <= length1
            && phase1opt(ud, rl, fb, selfSym, length1, -1) == 0) {
            if (isAborted) return solution.isFound ? solution.toString() : "Error 8";
            return !solution.isFound ? std::to_string(info::PROBE_LIMIT) : solution.toString();
        }
    }

    return !solution.isFound ? std::to_string(info::SHORT_DEPTH) : solution.toString();
}

int8_t min2phase::Search::phase1opt(coords::CoordCube ud, coords::CoordCube rl, coords::CoordCube fb, int64_t ssym, int8_t maxl, int8_t lm) {
    if (checkCallback(length1)) return 0;
    uint8_t axis, power, prun_ud, prun_rl, prun_fb, m;

#if MIN2PHASE_DEBUG > 1
    MIN2PHASE_OUTPUT("Phase 1 optimal.")
#endif

    if (ud.prun == 0 && rl.prun == 0 && fb.prun == 0 && maxl < 5) {
        maxDep2 = maxl;
        depth1 = length1 - maxl;
        return initPhase2Pre() == 0 ? 0 : 1;
    }

    for (axis = 0; axis < info::N_MOVES; axis += info::N_GROUP_MOVES) {
        if (axis == lm || axis == lm - 9)
            continue;

        for (power = 0; power < 3; power++) {
            m = axis + power;

            prun_ud = std::max(nodeUD[maxl].doMovePrun(ud, m),nodeUD[maxl].doMovePrunConj(ud, m));

            if (prun_ud > maxl)
                break;
            else if (prun_ud == maxl)
                continue;

            m = info::urfMove[2][m];

            prun_rl = std::max(nodeRL[maxl].doMovePrun(rl, m), nodeRL[maxl].doMovePrunConj(rl, m));
            if (prun_rl > maxl)
                break;
            else if (prun_rl == maxl)
                continue;

            m = info::urfMove[2][m];

            prun_fb = std::max(nodeFB[maxl].doMovePrun(fb, m), nodeFB[maxl].doMovePrunConj(fb, m));

            if (prun_ud == prun_rl && prun_rl == prun_fb && prun_fb != 0)
                prun_fb++;

            if (prun_fb > maxl)
                break;
            else if (prun_fb == maxl)
                continue;

            m = info::urfMove[2][m];

            move[length1 - maxl] = m;
            valid1 = std::min((int8_t) valid1, (int8_t)(length1 - maxl));

            if (phase1opt(nodeUD[maxl], nodeRL[maxl], nodeFB[maxl], ssym & coords::coords.moveCubeSym[m], maxl - 1, axis) == 0)
                return 0;
        }
    }

    return 1;
}

// 融合多核终止信号，动态同步剪枝深度并控制时间窗口
bool min2phase::Search::checkCallback(int8_t depth) {
    if (isAborted || (m_parallelCtx != nullptr && m_parallelCtx->aborted.load())) {
        isAborted = true;
        return true;
    }

    if (m_parallelCtx != nullptr) {
        // alpha-beta 风格深度同步：若其他核心已找到更短解，动态压缩当前核心上限
        int8_t globalBest = m_parallelCtx->bestSolLen;
        if (globalBest < solLen) {
            solLen = globalBest;
            maxDep2 = std::min((int32_t)info::P1_LENGTH, int32_t(solLen - length1 - 1));
        }

        // 若已产生首解且非持续压榨模式下超过 150 毫秒，则强行退出
        if (m_parallelCtx->hasSolution.load() && (verbose & 0x10) == 0) {
            auto now = std::chrono::high_resolution_clock::now();
            auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(now - m_parallelCtx->firstSolTime).count();
            if (elapsedMs > 150) {
                m_parallelCtx->aborted.store(true);
                isAborted = true;
                return true;
            }
        }
    }

    if (m_callback != nullptr) {
        if (--checkCountdown <= 0) {
            checkCountdown = 1000000;

            int64_t currentTotal = 1000000;
            if (m_parallelCtx != nullptr) {
                currentTotal = m_parallelCtx->totalNodes.fetch_add(1000000) + 1000000;
            } else {
                totalNodes += 1000000;
                currentTotal = totalNodes;
            }

            if (m_callback->isCancelled()) {
                isAborted = true;
                if (m_parallelCtx != nullptr) {
                    m_parallelCtx->aborted.store(true);
                }
                return true;
            }
            m_callback->onProgress(currentTotal, depth);
        }
    }
    return false;
}