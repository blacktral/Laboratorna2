package com.example;

import org.openjdk.jmh.annotations.*;

import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime) // Вимірювання часу в середньому
@OutputTimeUnit(TimeUnit.MILLISECONDS) // Виводити час в мілісекундах
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread) // Створення стану на кожен потік
public class MatrixBenchmark {
    private static int m = 300, n = 300, k = 300, p = 8;
    private int[][] A, B, C;
    private AtomicBoolean[][] isCalculating;

    // Метод для ініціалізації матриць (визначення їх розмірів і заповнення випадковими числами)
    @Setup(Level.Invocation)
    public void setUp() {
        initializeMatrices();
    }

    private void initializeMatrices() {
        Random rand = new Random();
        A = new int[m][n];
        B = new int[n][k];
        C = new int[m][k];
        isCalculating = new AtomicBoolean[m][k];

        // Заповнення матриць випадковими числами
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                A[i][j] = rand.nextInt(10);

        for (int i = 0; i < n; i++)
            for (int j = 0; j < k; j++)
                B[i][j] = rand.nextInt(10);

        for (int i = 0; i < m; i++)
            for (int j = 0; j < k; j++)
                isCalculating[i][j] = new AtomicBoolean(false);
    }

    // Тест для традиційного пулу потоків
    @Benchmark
    public void benchmarkTraditionalThreadPool() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(p);
        executeMultiplication(executor);
    }

    // Тест для віртуальних потоків (необмежено)
    @Benchmark
    public void benchmarkVirtualThreadsUnlimited() throws InterruptedException {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        executeMultiplication(executor);
    }


    // Тест для віртуальних потоків з обмеженням за допомогою семафору
    @Benchmark
    public void benchmarkVirtualThreadsLimited() throws InterruptedException {
        Semaphore semaphore = new Semaphore(p); // Обмежуємо кількість одночасно активних потоків
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        int totalTasks = m * k; // Кількість клітинок, які треба обчислити
        CountDownLatch latch = new CountDownLatch(totalTasks);

        for (int i = 0; i < m; i++) {
            for (int j = 0; j < k; j++) {
                final int row = i;
                final int col = j;

                executor.submit(() -> {
                    try {
                        semaphore.acquire();
                        try {
                            int sum = 0;
                            for (int l = 0; l < n; l++) {
                                sum += A[row][l] * B[l][col];
                            }
                            C[row][col] = sum;
                        } finally {
                            semaphore.release();
                            latch.countDown();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }

        latch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }


    // Метод виконання множення матриць (з використанням пулу потоків)
    private void executeMultiplication(ExecutorService executor) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(p);
        for (int i = 0; i < p; i++) {
            executor.submit(() -> {
                calculateCells();
                latch.countDown();
            });
        }
        latch.await();
        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);
    }

    // Метод для розрахунку елементів матриці C
    private void calculateCells() {
        outer:
        while (true) {
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < k; j++) {
                    if (isCalculating[i][j].compareAndSet(false, true)) {
                        for (int l = 0; l < n; l++) {
                            C[i][j] += A[i][l] * B[l][j];
                        }
                        continue outer;
                    }
                }
            }
            break;
        }
    }

    // Запуск JMH бенчмарку
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
