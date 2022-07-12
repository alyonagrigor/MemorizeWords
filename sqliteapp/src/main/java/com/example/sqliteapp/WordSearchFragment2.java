package com.example.sqliteapp;

/**
 * возможно перееисать на стрингбилдер
 * возможно добавить генерацию букв из др.алфавитов
 * возм. доб генерацию в зависимости от языка
 * возм. добавить вычеркивание
 * возм. запретить персечение в одной ориентации
 * возм. увеличить до 12 на 12 - только если делать другой фрагмент для планшетов
 * доб. вырезание слов из предложения и проверку на отсут.цифр
 */

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.sqliteapp.databinding.FragmentWordSearchBinding;

import java.util.ArrayList;
import java.util.Random;

public class WordSearchFragment2 extends Fragment
        implements View.OnTouchListener
        {

    private static final String TAG = "myLogs";
    private FragmentWordSearchBinding binding;
    DatabaseHelper databaseHelper;
    SQLiteDatabase db;
    Cursor wordsCursor;
    int linesCount = 0, ver = 0, hor = 0, horCount = 0, verCount = 0, direction = 1;
    final int CELLS_AMOUNT = 10; // количество ячеек по горизонтали и по вертикали
    final int LIMIT = 3; // минимальная длина слова
    boolean isUsed, hasSucceed, isTheSame;
    String currentWord, letter, substr1, substr2;
    Random rand = new Random();
    Cell[] cellsArray = new Cell[CELLS_AMOUNT * CELLS_AMOUNT]; //массив для хранения ячеек
    CellBorder[][] cellBorderArray = new CellBorder[CELLS_AMOUNT][CELLS_AMOUNT]; //массив для
            // хранения границ ячеек
    Cell appropriateCell;
    ArrayList<Integer> usedList = new ArrayList<Integer>(); //коллекция для хранения уже
    // использованных слов по айди в бд
    ArrayList<Cell> appropriateCellsList = new ArrayList<Cell>(); //коллекция appropriateCells,
    //использованных в данном цикле
    GestureDetector mGestureDetector = new GestureDetector(getActivity(), new MyGestureDetector());

// Add a OnTouchListener into view

    public WordSearchFragment2() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentWordSearchBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        databaseHelper = new DatabaseHelper(getActivity());
        databaseHelper.create_db();
    }

    @Override
    public void onResume() {
        super.onResume();
        db = databaseHelper.open();
        wordsCursor = db.rawQuery("select * from " + DatabaseHelper.TABLE
                        // нужно ли исключать слова?
                        + " WHERE study = 1"
                , null);
        wordsCursor.moveToFirst();
        linesCount = wordsCursor.getCount();
        // проверка, что в бд не менее 20 слов
        if (wordsCursor.getCount() < 20) {
            binding.wsImpossible.setVisibility(View.VISIBLE);
            binding.wsText.setVisibility(View.GONE);
            binding.tableLayout.setVisibility(View.GONE);
        } else {
            fillArray(); //заполняем массив ячеек объектами

            //устанавливаем слушатели
            for (Cell item: cellsArray) {
            /*    item.getCellId().setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        v.performClick();
                        return mGestureDetector.onTouchEvent(event);
                    }
                })*/
                item.getCellId().setOnTouchListener(this);
            }

            //в цикле по очереди вставляем слова двумя разными методами: один добавляет слова так,
            //чтобы они не пересекались с другими, второй сработает только если есть совпадающие
            // ячейки с другим словом
            for (int j = 0; j < CELLS_AMOUNT * CELLS_AMOUNT; j++) {
                clearVariables();
                getRandomDirection();
                getRandomWord();
                getRandomPosition();
                if (!checkWordWithoutAppropriate()) { //если нужные ячейки свободны, то вставляем слово
                    writeWordWithoutAppropriate();
                }

                // ЦИКЛ ДЛЯ ДОБАВЛЕНИЯ СЛОВ С ПЕРЕСЕЧЕНИЯМИ
                clearVariables();
                appropriateCell = null;
                Log.d(TAG, "запускаем цикл раз номер " + j);
                // получаем рандомное слово
                getRandomWord();
                Log.d(TAG, "получаем слово номкр " + j + " " + currentWord);
                //подбираем ячейку, в которой вставлена буква, совпадающая с буквой в новом слове -
                //объект appropriateCell
                findAppropriateCell();

                if (appropriateCell != null) { //если есть совпадающие буквы (appropriateCell == !null)
                    appropriateCellsList.add(appropriateCell);
                    boolean bool = placeWordWithAppropriate();
                    //если с первой попытки вставить не удалось, то пробуем подобрать другую
                    // appropriateCell CELLS_AMOUNT * 2 раз
                    if (!bool) {
                        appropriateCell = null;
                        for (int y = 0; y < CELLS_AMOUNT * 2; y++) {
                            findAppropriateCell();
                            if (appropriateCell != null) {
                                //цикл для проверки, что это не та же ячейка
                                isTheSame = false;
                                for (int k = 0, listSize = appropriateCellsList.size(); k < listSize; k++) {
                                    Cell item = appropriateCellsList.get(k);
                                    if (item.getCellId() == appropriateCell.getCellId()) {
                                        isTheSame = true;
                                        break;
                                    }
                                }
                                appropriateCellsList.add(appropriateCell);
                                break;
                            }
                        }
                    }
                    // если удалось подобрать appropriateCell, то пытаемся вставить еще 1 раз
                    if (!hasSucceed && appropriateCell != null && !isTheSame) {
                        clearVariables();
                        placeWordWithAppropriate();
                    }
                }
            }


            /* КОНЕЦ ЦИКЛА!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! */

            //в конце выводим количество слов по гор. и вертикали в textView
            binding.wsText.setText(getString(R.string.findWords, horCount, verCount));

            //заполняем оставшиеся ячейки случайными буквами, чтобы выглядело естественно, чередуем
            //гласные и согласные, также убираем редко встречающиеся буквы
    /*    String consonants = "BCDFGHJKLMNPRST";
        String vowels = "AEIOU";
        char c;
        int counter = 1; //счетчик для чередования: в нечетные разы - гласные, в четные - согласные
        for (Cell item: cellsArray) {
            if (item.getLetter().equals("")) {
                if (counter % 2 != 0) {
                    c = vowels.charAt(rand.nextInt(vowels.length()));
                } else {
                    c = consonants.charAt(rand.nextInt(consonants.length()));
                }
                    String str = String.valueOf(c);
                    item.setLetter(str);
                    item.getCellId().setText(str);
                    counter++;
            }
        }*/

        // для обработки движений строим массив объектов, хранящих границы CellBorder в пикселях
            // top - right - bottom - left
            for (int v = 0; v < CELLS_AMOUNT; v++) { //координаты по вертикали
                Log.d(TAG, "внешний массив, шаг: " + v);
                for (int g = 0; g < CELLS_AMOUNT; g++) {//координаты по горизонтали
                    Log.d(TAG, "внутренний массив, шаг: " + g);
                    cellBorderArray[g][v] = new CellBorder(g + 1, v + 1, //записывыаем координаты ячейки
                        pleaseCalculateTopBorder(v), //получаем верхнюю границу
                        pleaseCalculateRightBorder(g), //получаем правую границу
                        pleaseCalculateLeftBorder(g), //левую границу
                        pleaseCalculateBottomBorder(v)//нижнюю границу
                    );
                    Log.d(TAG, "формируем ячейку: v = "+ v + "g = " + g);
                    Log.d(TAG, String.valueOf(cellBorderArray[g][v].getCoordVer()));
                    Log.d(TAG, String.valueOf(cellBorderArray[g][v].getCoordHor()));
                    Log.d(TAG, String.valueOf(cellBorderArray[g][v].getTopBorder()));
                    Log.d(TAG, String.valueOf(cellBorderArray[g][v].getRightBorder()));
                    Log.d(TAG, String.valueOf(cellBorderArray[g][v].getBottomBorder()));
                    Log.d(TAG, String.valueOf(cellBorderArray[g][v].getLeftBorder()));
                    Log.d(TAG, "------------------");
                }
            }
           /* Log.d(TAG, "массив: " + (Arrays.asList(cellsArray)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
               Stream.of(cellBorderArray).forEach(System.out::println);
            }*/

        } //кавычка от разветвления после заглушки
    } // конец ONRESUME//////////////////////////////////////////////////////////////////

    public void findAppropriateCell() {
        //проверяем, есть ли совпадающие буквы в таблице
        for (Cell item: cellsArray) { //проверяем совпадение букв в уже заполненных ячейках с новым словом
            if (!item.getLetter().equals("")) { //проверка что ячейка не пустая
                for (int i = 0; i < currentWord.length(); i++) {
                    if (item.getLetter().equals(Character.toString(currentWord.charAt(i)))) {
                        appropriateCell = new Cell(item.getHor(), item.getVer(),
                                item.getCellId(), item.getLetter());
                        break; //если подходящая ячейка найдена, выходим из внутреннего цикла
                    }
                }
                if (appropriateCell != null) {
                    break;
                } //если подходящая ячейка найдена, выходим из внешнего цикла
            }
        }
    }

    public boolean placeWordWithAppropriate() {
        //разрезаем слово на 2 подстроки, до и после совпадающей буквы, не включая эту букву;
        //если сопадает первая или последняя буква, то первая или вторая подстрока может оказаться
        // пустой, в этом случае мы явно прописываем подстроки как пустые
        int x = currentWord.indexOf(appropriateCell.getLetter());
        if (x == 0) {
            substr1 = "";
            substr2 = currentWord.substring(1);
            Log.d(TAG, "первая подстрока = " + substr1);
            Log.d(TAG, "вторая подстрока = " + substr2);
        } else if (x == currentWord.length() - 1) {
            substr1 = currentWord.substring(0, currentWord.length() - 1);
            substr2 = ("");
            Log.d(TAG, "первая подстрока = " + substr1);
            Log.d(TAG, "вторая подстрока = " + substr2);
        } else {
            substr1 = currentWord.substring(0, x);
            substr2 = currentWord.substring(x + 1);
            Log.d(TAG, "первая подстрока = " + substr1);
            Log.d(TAG, "вторая подстрока = " + substr2);
        }

        //проверяем, не попадет ли последняя буква слова за границы поля
        //назначаем переменные для провреки
        boolean isTopDirectionAvailable = false;
        boolean isLeftDirectionAvailable = false;
        boolean isBottomDirectionAvailable = false;
        boolean isRightDirectionAvailable = false;

        if (!substr1.equals("")) {
            //проверяем верхнее направление
            isTopDirectionAvailable = appropriateCell.getHor() - substr1.length() > 0;
            Log.d(TAG, "isTopDirectionAvailable = " + isTopDirectionAvailable);
            //левое направление
            isLeftDirectionAvailable = appropriateCell.getVer() - substr1.length() > 0;
            Log.d(TAG, "isLeftDirectionAvailable = " + isLeftDirectionAvailable);
        }
        if (!substr2.equals("")) {//substr2
            //проверяем нижнее направление
            isBottomDirectionAvailable = appropriateCell.getHor() + substr2.length() < CELLS_AMOUNT + 1;
            Log.d(TAG, "isBottomDirectionAvailable = " + isBottomDirectionAvailable);

            //правое направление
            isRightDirectionAvailable = appropriateCell.getVer() + substr2.length() < CELLS_AMOUNT + 1;
            Log.d(TAG, "isRightDirectionAvailable = " + isRightDirectionAvailable);
        }

        //запускаем функции, которые вписывают буквы в ячейки
        //УСЛОВИЕ 1. Если подстрока substr1 пустая, то вписываем substr2 вправо или вниз
        if (substr1.equals("") && !substr2.equals("")) {
            //если оба направления недоступны, возвращаем false
            if (!isRightDirectionAvailable && !isBottomDirectionAvailable) {
                hasSucceed = false; //конец логики метода
            } else {
                //вычисляем, можно ли записать вправо или вниз, только если доступно это направление,
                //иначе оставляем false
                boolean areRightCellsAvailable = false,
                        areBottomCellsAvailable = false;

                if (isRightDirectionAvailable) {
                    areRightCellsAvailable = !checkRight();
                }
                if (isBottomDirectionAvailable) {
                    areBottomCellsAvailable = !checkBottom();
                }

                if (!areBottomCellsAvailable && !areRightCellsAvailable) {//если нельзя ни вправо, ни вниз, то
                    hasSucceed = false; //конец логики метода
                }
                if (isRightDirectionAvailable && areRightCellsAvailable) {//проверяем, можно ли вписать вправо
                    writeRight();
                    horCount++;
                    Log.d(TAG, "horCount++ = " + horCount);
                    usedList.add(wordsCursor.getInt(0));
                    //если прошло успешно, записываем в horCount и в список использованных
                    hasSucceed = true;//конец логики метода
                } else if ((!areRightCellsAvailable || !isRightDirectionAvailable)
                        && isBottomDirectionAvailable && areBottomCellsAvailable) {
                    //если нельзя вписать вправо, пытаемся вписать вниз
                    writeBottom();
                    verCount++; //если прошло успешно, записываем в verCount и в список использованных
                    Log.d(TAG, "verCount++ = " + verCount);
                    usedList.add(wordsCursor.getInt(0));
                    hasSucceed = true; //конец логики метода
                } else if ((!areRightCellsAvailable || !isRightDirectionAvailable)
                        && (!isBottomDirectionAvailable || !areBottomCellsAvailable)) {
                    //если нельзя вписать никуда, то
                    hasSucceed = false;
                }
            }


            //УСЛОВИЕ 2. Если подстрока substr2 пустая
        } else if (!substr1.equals("") && substr2.equals("")) { //если пустая substr2
            //  то вписываем substr1 влево
            //если оба направления недоступны, возвращаем false
            if (!isLeftDirectionAvailable && !isTopDirectionAvailable) {
                hasSucceed = false; //конец логики метода
            } else {
                //вычисляем, можно ли записать влево или вверх, только если доступно это направление,
                //иначе оставляем false
                boolean areLeftCellsAvailable = false,
                        areTopCellsAvailable = false;

                if (isLeftDirectionAvailable) {
                    areLeftCellsAvailable = !checkLeft();
                }
                if (isTopDirectionAvailable) {
                    areTopCellsAvailable = !checkTop();
                }

                if (!areTopCellsAvailable && !areLeftCellsAvailable) {//если в оба направления вписать нельзя, то
                    hasSucceed = false;
                } //конец логики метода
                if (isLeftDirectionAvailable && areLeftCellsAvailable) {//если влево вписать можно, то
                    writeLeft();
                    horCount++;
                    Log.d(TAG, "horCount++ = " + horCount);
                    usedList.add(wordsCursor.getInt(0));
                    //если прошло успешно, записываем в horCount и в список использованных
                    hasSucceed = true;//конец логики метода
                    //если влево вписать нельзя, вписываем вврх
                } else if ((!areLeftCellsAvailable || !isLeftDirectionAvailable)
                        && isTopDirectionAvailable && areTopCellsAvailable) {
                    writeTop();
                    verCount++;
                    Log.d(TAG, "verCount++ = " + verCount);
                    usedList.add(wordsCursor.getInt(0));
                    hasSucceed = true;
                } else if ((!areLeftCellsAvailable || !isLeftDirectionAvailable)
                        && (!isTopDirectionAvailable || !areTopCellsAvailable)) {
                    //если нельзя вписать никуда, то
                    hasSucceed = false;
                }
            }

            //УСЛОВИЕ 3. Если обе подстроки не пустые
        } else if ((!substr1.equals("") && !substr2.equals(""))) {
            //проверяем, доступны ли направления
            //если оба направления недоступны, возвращаем false
            if ((!isLeftDirectionAvailable || !isRightDirectionAvailable)
                    && (!isTopDirectionAvailable || !isBottomDirectionAvailable)) {
                hasSucceed = false; //конец логики метода
            } else {
                boolean areLeftCellsAvailable = false,
                        areTopCellsAvailable = false,
                        areRightCellsAvailable = false,
                        areBottomCellsAvailable = false;

                if (isLeftDirectionAvailable) {
                    areLeftCellsAvailable = !checkLeft();
                }
                if (isTopDirectionAvailable) {
                    areTopCellsAvailable = !checkTop();
                }
                if (isRightDirectionAvailable) {
                        areRightCellsAvailable = !checkRight();
                }
                if (isBottomDirectionAvailable) {
                    areBottomCellsAvailable = !checkBottom();
                }

                if ((!areTopCellsAvailable || !areBottomCellsAvailable)
                        && (!areLeftCellsAvailable || !areRightCellsAvailable)) {//если в оба направления вписать нельзя, то
                    hasSucceed = false;
                } //конец логики метода
                //вписываем горизонтально
                if (isLeftDirectionAvailable && isRightDirectionAvailable
                        && areLeftCellsAvailable && areRightCellsAvailable) { //если можно вписать слева и справа, то
                    writeRight();
                    writeLeft();
                    horCount++;
                    Log.d(TAG, "horCount++ = " + horCount);
                    usedList.add(wordsCursor.getInt(0));
                    //если прошло успешно, записываем в horCount и в список использованных
                    hasSucceed = true;//конец логики метода
                } else if ((!areLeftCellsAvailable || !areRightCellsAvailable
                        || !isLeftDirectionAvailable || !isRightDirectionAvailable)
                        && isTopDirectionAvailable && isBottomDirectionAvailable //если возможно вписать вверх и вниз
                        && areTopCellsAvailable && areBottomCellsAvailable) {//то пытаемся вписать вверх
                    writeTop();
                    writeBottom();
                    verCount++;
                    Log.d(TAG, "verCount++ = " + verCount);
                    usedList.add(wordsCursor.getInt(0));
                    hasSucceed = true;
                    //если в обоих направлениях вписать не удалось
                } else if ((!areBottomCellsAvailable || !areTopCellsAvailable
                        || !isBottomDirectionAvailable || !isTopDirectionAvailable)
                        && (!areRightCellsAvailable || !areLeftCellsAvailable
                        || !isRightDirectionAvailable || !isLeftDirectionAvailable)) {
                    hasSucceed = false;
                }
            }
        }
        return hasSucceed;
    }

    public boolean checkTop() {
        boolean flagTop = false;
        Log.d(TAG, "flagTop = false ");
        Log.d(TAG, "слово для вставки " + currentWord);
        Log.d(TAG, "строка для размещения сверху " + substr1);
        Log.d(TAG, "substr1.length() = " + substr1.length());
        Log.d(TAG, "коорд. appropriateCell по гор " + appropriateCell.getHor() +  ", по верт. " + appropriateCell.getVer());

        for (int i = appropriateCell.getHor() - substr1.length(), k = 0; k < substr1.length(); i++, k++) {
            Log.d(TAG, "входим в цикл для проверки в направлении Top");
            Log.d(TAG, "i = " + i + ", k = " + k);
            letter = Character.toString(substr1.charAt(k)); //получаем букву
            Log.d(TAG, "буква для размещения " + letter);
            for (Cell item: cellsArray) {
                if (item.getHor() == i && item.getVer() == appropriateCell.getVer()) {
                    Log.d(TAG, "коорд ячейки по гор: " + i + ", по верт. " + appropriateCell.getVer());
                    if (item.getLetter().equals("")) {// проверка на незанятость ячейки
                        Log.d(TAG, "для буквы " + k + " получаем flagTop = false ");
                    //    break;
                    } else if (item.getLetter().equals(letter)) {
                        //проверяем, совпадают ли буквы уже вставленная и буква нового слова
                        Log.d(TAG, "для буквы " + k + " получаем flagTop = false ");
                    //    break;
                    } else if (!item.getLetter().equals("") && !item.getLetter().equals(letter)) {  //если вставлена другая буква, то стираем записанные буквы
                        //ставим флаг на выход из внешнего цикла
                        flagTop = true;
                        Log.d(TAG, "для буквы " + k + " получаем flagTop = true ");
                        break;
                    }
                }
            }
            if (flagTop) { break ; }
        }
        Log.d(TAG, "вернулся flagTop = " + flagTop);
        return flagTop;
    }

    public void writeTop() {
        for (int i = appropriateCell.getHor() - substr1.length(), k = 0; k < substr1.length(); i++, k++) {
            Log.d(TAG, "входим в цикл для записи в направлении Top");
            letter = Character.toString(substr1.charAt(k)); //получаем букву
            for (Cell item: cellsArray) {
                if (item.getHor() == i && item.getVer() == appropriateCell.getVer()) {
                    Log.d(TAG, "записываем в ячейку по гор: " + i + ", по верт. " + appropriateCell.getVer());
                    item.getCellId().setText(letter); // в ячейку
                    item.setLetter(letter);// и в объект
                }
            }
        }
    }

    public boolean checkLeft() {
        boolean flagLeft = false;
        for (int i = appropriateCell.getVer() - substr1.length(), k = 0; k < substr1.length(); i++, k++) {
            letter = Character.toString(substr1.charAt(k)); //получаем букву
            for (Cell item: cellsArray) {
                if (item.getHor() == appropriateCell.getHor() && item.getVer() == i) {
                    if (item.getLetter().equals("")) {// проверка на незанятость ячейки
                        //если ячейка пустая, идем проверять следующую
                        break;
                    } else if (item.getLetter().equals(letter)) {
                        //проверяем, совпадают ли буквы уже вставленная и буква нового слова
                        // идем проверять следующую
                        break;
                    } else if (!item.getLetter().equals("") && !item.getLetter().equals(letter)) {  //если вставлена другая буква, то стираем записанные буквы
                        //ставим флаг на выход из внешнего цикла
                        flagLeft = true;
                        break;
                    }
                }
            }
            if (flagLeft) { break ;}
        }
        return flagLeft;
    }

    public void writeLeft() {
        for (int i = appropriateCell.getVer() - substr1.length(), k = 0; k < substr1.length(); i++, k++) {
            letter = Character.toString(substr1.charAt(k)); //получаем букву
            for (Cell item: cellsArray) {
                if (item.getHor() == appropriateCell.getHor() && item.getVer() == i) {
                        item.getCellId().setText(letter); // в ячейку
                        item.setLetter(letter);// и в объект
                }
            }
        }
    }

    public boolean checkRight() {
        boolean flagRight = false;
        for (int i = appropriateCell.getVer() + 1, k = 0; k < substr2.length(); i++, k++) {
            letter = Character.toString(substr2.charAt(k)); //получаем букву
            for (Cell item: cellsArray) { //находим объект cell с координатами appropriateCell.getHor
                // и i по вертикали
                if (item.getHor() == appropriateCell.getHor() && item.getVer() == i) {
                    if (item.getLetter().equals("")) {// проверка на незанятость ячейки
                        //если ячейка пустая, выходим из цикла
                        break;
                    } else if (item.getLetter().equals(letter)) {
                        //проверяем, совпадают ли буквы уже вставленная и буква нового слова
                        // если совпадают, выходим из цикла
                        break;
                    } else if (!item.getLetter().equals("") && !item.getLetter().equals(letter)) {  //если вставлена другая буква, то стираем записанные буквы
                        //если ячейка занята, выходим из метода и возращаем тру
//ставим флаг на выход из внешнего цикла
                        flagRight = true;
                        break;
                    }
                }
            }
            if (flagRight) { break ;}
        }
        return flagRight;
    }

    public void writeRight() {
        for (int i = appropriateCell.getVer() + 1, k = 0; k < substr2.length(); i++, k++) {
            letter = Character.toString(substr2.charAt(k)); //получаем букву
            for (Cell item: cellsArray) { //находим объект cell с координатами appropriateCell.getHor
                // и i по вертикали
                if (item.getHor() == appropriateCell.getHor() && item.getVer() == i) {
                        item.getCellId().setText(letter); // в ячейку
                        item.setLetter(letter);// и в объект
                }
            }
        }
    }

    public boolean checkBottom() {
        boolean flagBottom = false;
        for (int i = appropriateCell.getHor() + 1, k = 0; k < substr2.length(); i++, k++) {
            letter = Character.toString(substr2.charAt(k)); //получаем букву
            for (Cell item: cellsArray) { //находим объект cell с координатами appropriateCell.getVer
                // и i по горизонтали
                if (item.getHor() == i && item.getVer() == appropriateCell.getVer()) {
                    if (item.getLetter().equals("")) {// проверка на незанятость ячейки
                        //если ячейка пустая, выходим из внутреннего цикла и проверяем дальше
                        break;
                    } else if (item.getLetter().equals(letter)) {
                        //проверяем, совпадают ли буквы уже вставленная и буква нового слова
                        // то выходим из цикла и проверяем дальше
                        break;
                    } else if (!item.getLetter().equals("") && !item.getLetter().equals(letter)) {
                        //ставим флаг на выход из внешнего цикла
                        flagBottom = true;
                        break;
                    }
                }
            }
            if (flagBottom) { break ;}
        }
        return flagBottom;
    }

    public void writeBottom() {
        for (int i = appropriateCell.getHor() + 1, k = 0; k < substr2.length(); i++, k++) {
            letter = Character.toString(substr2.charAt(k)); //получаем букву
            for (Cell item: cellsArray) { //находим объект cell с координатами appropriateCell.getVer
                // и i по горизонтали
                if (item.getHor() == i && item.getVer() == appropriateCell.getVer()) {
                    item.getCellId().setText(letter); // в ячейку
                    item.setLetter(letter);// и в объект
                }
            }
        }
    }

    public boolean checkWordWithoutAppropriate() {
        boolean flagWithoutAppropriate = false;
        Log.d(TAG, "checkWordWithoutAppropriate() ");
        if (direction == 1) { //для гориз.ориентации
            for (int i = ver, k = 0; k < currentWord.length(); i++, k++) {
                letter = Character.toString(currentWord.charAt(k)); //получаем букву
                for (Cell item: cellsArray) { //находим объект cell с координатами i и ver
                    if (item.getVer() == i && item.getHor() == hor) {
                        if (item.getLetter().equals("")) {// проверка на незанятость ячейки
                            Log.d(TAG, "пустая ячйека по горизонтали ");
                            break; //выходим из цикла и переходим к следующей ячейке
                        } else if (item.getLetter().equals(letter)) {
                            //проверяем, совпадают ли буквы уже вставленная и буква нового слова
                            // то переходим к следующей ячейке на пути - выходим из цикла
                            Log.d(TAG, "пропустили совпадающую букву по горизонтали " + letter);
                                break;
                        } else {  //если вставлена другая буква, то отдаем флаг
                            flagWithoutAppropriate = true;
                            Log.d(TAG, " flag = true ");
                        }
                    }
                }
                if (flagWithoutAppropriate) { break; }
            }
        }

        if (direction == 2) {//для верт.ориентации
            for (int i = hor, k = 0; k < currentWord.length(); i++, k++) {
                letter = Character.toString(currentWord.charAt(k)); //получаем букву
                for (Cell item: cellsArray) { //находим объект cell с координатами hor и i
                    if (item.getHor() == i && item.getVer() == ver) {
                        if (item.getLetter().equals("")) {// проверка на незанятость ячейки
                            Log.d(TAG, "разметсили букву по вертикали " + letter);
                            Log.d(TAG, "hor = " + i);
                            Log.d(TAG, "ver = " + item.getHor());
                            break; //выходим из цикла и переходим к следующей ячейке
                        } else if (item.getLetter().equals(letter)) {
                            Log.d(TAG, "буквы совпадают " + letter);
                            //проверяем, совпадают ли буквы уже вставленная и буква нового слова
                            // то переходим к следующей ячейке на пути - выходим из цикла
                            break;
                        } else {  //если вставлена другая буква, то стираем записанные буквы
                            flagWithoutAppropriate = true;
                            Log.d(TAG, " flag = true ");
                        }
                    }
                }
            if (flagWithoutAppropriate) { break; }
            }
        }
        return flagWithoutAppropriate;
    }

    public void writeWordWithoutAppropriate() {
        boolean flagWithoutAppropriate = false;
        Log.d(TAG, "writeWordWithoutAppropriate() ");
        if (direction == 1) { //для гориз.ориентации
            for (int i = ver, k = 0; k < currentWord.length(); i++, k++) {
                letter = Character.toString(currentWord.charAt(k)); //получаем букву
                for (Cell item: cellsArray) { //находим объект cell с координатами i и ver
                    if (item.getHor() == hor && item.getVer() == i) {
                        item.getCellId().setText(letter);
                        item.setLetter(letter);
                        Log.d(TAG, "разметсили букву по горизонтали " + letter);
                        Log.d(TAG, "hor = " + item.getHor());
                        Log.d(TAG, "ver = " + i);
                    }
                }
            }
        }

        if (direction == 2) {//для верт.ориентации
            for (int i = hor, k = 0; k < currentWord.length(); i++, k++) {
                letter = Character.toString(currentWord.charAt(k)); //получаем букву
                for (Cell item: cellsArray) { //находим объект cell с координатами hor и i
                    if (item.getHor() == i && item.getVer() == ver) {
                            item.getCellId().setText(letter);
                            item.setLetter(letter);
                            Log.d(TAG, "разметсили букву по вертикали " + letter);
                            Log.d(TAG, "hor = " + i);
                            Log.d(TAG, "ver = " + item.getHor());
                    }
                }
            }
        }

        // в конце записываем в коллекцию использованных

        usedList.add(wordsCursor.getInt(0));
            // и в количество горизонтально или вертикально расположенных слов
        if (direction == 1) {
            horCount++;
            Log.d(TAG, "horCount++ = " + horCount);
        } else if (direction == 2) {
            verCount++;
            Log.d(TAG, "verCount++ = " + verCount);
        }
    }

    public void getRandomWord () { //добавлен функционал для получения отдельных слов из фразы,
        //на случай, если пользователь записывал фразы или предложения
        do  { wordsCursor.moveToPosition(rand.nextInt(linesCount));
            checkUsed();
            currentWord = wordsCursor.getString(1);
        } while (isUsed ||
                currentWord.length() > CELLS_AMOUNT ||
                currentWord.length() < LIMIT ||
                currentWord.contains(" "));
        /*добавить вырезание 1 слова из предложения, добавить проверку, что не содержит цифр и
        др. символов*/
        currentWord = currentWord.toUpperCase(); //во избежание путаницы используем всегда заглавные буквы
    }

    public void getRandomPosition () {
        // генерируем координаты для первой буквы
        if (direction == 1) { //если ориент. гориз.
            hor = rand.nextInt(CELLS_AMOUNT) + 1; // по горизонтали любое значение
            ver = rand.nextInt(CELLS_AMOUNT - currentWord.length()) + 1; // генерируем
            // значение в таком диапазоне, чтобы слово не вышло за пределы поля
        }

        if (direction == 2) { // если слово располагаем по вертикали, тогда наоборот
            hor = rand.nextInt(CELLS_AMOUNT - currentWord.length()) + 1;
            ver = rand.nextInt(CELLS_AMOUNT) + 1; // по вертикали любое значение
        }
    }

    public void getRandomDirection() {
        direction = rand.nextInt(2) + 1;
    }

    public void checkUsed() {
        isUsed = false;
        for (int i = 0, usedSize = usedList.size(); i < usedSize; i++) {
            Integer integer = usedList.get(i);
            if (integer == wordsCursor.getInt(0)) {
                isUsed = true;
                break;
            }
        }
    }

    public void clearVariables() {
        currentWord = "";
        letter = "";
        substr1 = "";
        substr2 = "";
        hor = 0;
        ver = 0;
        appropriateCellsList.clear();
        hasSucceed = false;
        isUsed = false;
        isTheSame = false;
    }

   public void fillArray() {
   /*      cellsArray[0] = new Cell(1, 1, binding.h1v1, "");
        cellsArray[1] = new Cell(1, 2, binding.h1v2, "");
        cellsArray[2] = new Cell(1, 3, binding.h1v3, "");
        cellsArray[3] = new Cell(1, 4, binding.h1v4, "");
        cellsArray[4] = new Cell(1, 5, binding.h1v5, "");
        cellsArray[5] = new Cell(1, 6, binding.h1v6, "");
        cellsArray[6] = new Cell(1, 7, binding.h1v7, "");
        cellsArray[7] = new Cell(1, 8, binding.h1v8, "");
        cellsArray[8] = new Cell(1, 9, binding.h1v9, "");
        cellsArray[9] = new Cell(1, 10, binding.h1v10, "");
        cellsArray[10] = new Cell(2, 1, binding.h2v1, "");
        cellsArray[11] = new Cell(2, 2, binding.h2v2, "");
        cellsArray[12] = new Cell(2, 3, binding.h2v3, "");
        cellsArray[13] = new Cell(2, 4, binding.h2v4, "");
        cellsArray[14] = new Cell(2, 5, binding.h2v5, "");
        cellsArray[15] = new Cell(2, 6, binding.h2v6, "");
        cellsArray[16] = new Cell(2, 7, binding.h2v7, "");
        cellsArray[17] = new Cell(2, 8, binding.h2v8, "");
        cellsArray[18] = new Cell(2, 9, binding.h2v9, "");
        cellsArray[19] = new Cell(2, 10, binding.h2v10, "");
        cellsArray[20] = new Cell(3, 1, binding.h3v1, "");
        cellsArray[21] = new Cell(3, 2, binding.h3v2, "");
        cellsArray[22] = new Cell(3, 3, binding.h3v3, "");
        cellsArray[23] = new Cell(3, 4, binding.h3v4, "");
        cellsArray[24] = new Cell(3, 5, binding.h3v5, "");
        cellsArray[25] = new Cell(3, 6, binding.h3v6, "");
        cellsArray[26] = new Cell(3, 7, binding.h3v7, "");
        cellsArray[27] = new Cell(3, 8, binding.h3v8, "");
        cellsArray[28] = new Cell(3, 9, binding.h3v9, "");
        cellsArray[29] = new Cell(3, 10, binding.h3v10, "");
        cellsArray[30] = new Cell(4, 1, binding.h4v1, "");
        cellsArray[31] = new Cell(4, 2, binding.h4v2, "");
        cellsArray[32] = new Cell(4, 3, binding.h4v3, "");
        cellsArray[33] = new Cell(4, 4, binding.h4v4, "");
        cellsArray[34] = new Cell(4, 5, binding.h4v5, "");
        cellsArray[35] = new Cell(4, 6, binding.h4v6, "");
        cellsArray[36] = new Cell(4, 7, binding.h4v7, "");
        cellsArray[37] = new Cell(4, 8, binding.h4v8, "");
        cellsArray[38] = new Cell(4, 9, binding.h4v9, "");
        cellsArray[39] = new Cell(4, 10, binding.h4v10, "");
        cellsArray[40] = new Cell(5, 1, binding.h5v1, "");
        cellsArray[41] = new Cell(5, 2, binding.h5v2, "");
        cellsArray[42] = new Cell(5, 3, binding.h5v3, "");
        cellsArray[43] = new Cell(5, 4, binding.h5v4, "");
        cellsArray[44] = new Cell(5, 5, binding.h5v5, "");
        cellsArray[45] = new Cell(5, 6, binding.h5v6, "");
        cellsArray[46] = new Cell(5, 7, binding.h5v7, "");
        cellsArray[47] = new Cell(5, 8, binding.h5v8, "");
        cellsArray[48] = new Cell(5, 9, binding.h5v9, "");
        cellsArray[49] = new Cell(5, 10, binding.h5v10, "");
        cellsArray[50] = new Cell(6, 1, binding.h6v1, "");
        cellsArray[51] = new Cell(6, 2, binding.h6v2, "");
        cellsArray[52] = new Cell(6, 3, binding.h6v3, "");
        cellsArray[53] = new Cell(6, 4, binding.h6v4, "");
        cellsArray[54] = new Cell(6, 5, binding.h6v5, "");
        cellsArray[55] = new Cell(6, 6, binding.h6v6, "");
        cellsArray[56] = new Cell(6, 7, binding.h6v7, "");
        cellsArray[57] = new Cell(6, 8, binding.h6v8, "");
        cellsArray[58] = new Cell(6, 9, binding.h6v9, "");
        cellsArray[59] = new Cell(6, 10, binding.h6v10, "");
        cellsArray[60] = new Cell(7, 1, binding.h7v1, "");
        cellsArray[61] = new Cell(7, 2, binding.h7v2, "");
        cellsArray[62] = new Cell(7, 3, binding.h7v3, "");
        cellsArray[63] = new Cell(7, 4, binding.h7v4, "");
        cellsArray[64] = new Cell(7, 5, binding.h7v5, "");
        cellsArray[65] = new Cell(7, 6, binding.h7v6, "");
        cellsArray[66] = new Cell(7, 7, binding.h7v7, "");
        cellsArray[67] = new Cell(7, 8, binding.h7v8, "");
        cellsArray[68] = new Cell(7, 9, binding.h7v9, "");
        cellsArray[69] = new Cell(7, 10, binding.h7v10, "");
        cellsArray[70] = new Cell(8, 1, binding.h8v1, "");
        cellsArray[71] = new Cell(8, 2, binding.h8v2, "");
        cellsArray[72] = new Cell(8, 3, binding.h8v3, "");
        cellsArray[73] = new Cell(8, 4, binding.h8v4, "");
        cellsArray[74] = new Cell(8, 5, binding.h8v5, "");
        cellsArray[75] = new Cell(8, 6, binding.h8v6, "");
        cellsArray[76] = new Cell(8, 7, binding.h8v7, "");
        cellsArray[77] = new Cell(8, 8, binding.h8v8, "");
        cellsArray[78] = new Cell(8, 9, binding.h8v9, "");
        cellsArray[79] = new Cell(8, 10, binding.h8v10, "");
        cellsArray[80] = new Cell(9, 1, binding.h9v1, "");
        cellsArray[81] = new Cell(9, 2, binding.h9v2, "");
        cellsArray[82] = new Cell(9, 3, binding.h9v3, "");
        cellsArray[83] = new Cell(9, 4, binding.h9v4, "");
        cellsArray[84] = new Cell(9, 5, binding.h9v5, "");
        cellsArray[85] = new Cell(9, 6, binding.h9v6, "");
        cellsArray[86] = new Cell(9, 7, binding.h9v7, "");
        cellsArray[87] = new Cell(9, 8, binding.h9v8, "");
        cellsArray[88] = new Cell(9, 9, binding.h9v9, "");
        cellsArray[89] = new Cell(9, 10, binding.h9v10, "");
        cellsArray[90] = new Cell(10, 1, binding.h10v1, "");
        cellsArray[91] = new Cell(10, 2, binding.h10v2, "");
        cellsArray[92] = new Cell(10, 3, binding.h10v3, "");
        cellsArray[93] = new Cell(10, 4, binding.h10v4, "");
        cellsArray[94] = new Cell(10, 5, binding.h10v5, "");
        cellsArray[95] = new Cell(10, 6, binding.h10v6, "");
        cellsArray[96] = new Cell(10, 7, binding.h10v7, "");
        cellsArray[97] = new Cell(10, 8, binding.h10v8, "");
        cellsArray[98] = new Cell(10, 9, binding.h10v9, "");
        cellsArray[99] = new Cell(10, 10, binding.h10v10, "");*/
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
        db.close();
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        view.performClick();
        float x = motionEvent.getX();
        float y = motionEvent.getY();
        int curCellHor = 0; //переменные для записи координат текущей ячейки
        int curCellVer = 0;//переменные для записи координат текущей ячейки
        Cell firstCell = new Cell();//создаем объект, указывающий на первую ячейку, с
        // которой пользователь начал введение слова

        //когда пользователь проводит по слову, нужно изменить фон ячеек. Чтобы получить view ячеек,
        // возьмем координаты их границ. Если в процессе ACTION_MOVE x или y становится больше
        //кординат границы, значит пользователь завел палец в ячейку
        //ЕСЛИ ОДНОВРЕМЕННО СТАНОВЯТСЯ БОЛЬШЕ Х И У ИЛИ СТАНОВЯТСЯ МЕНЬШЕ, ТО СБРАСЫВАЕМ ВЫДЕЛЕНИЕ

        //создаем массивы объектов RowBorder, в котопые складываем пары значений
        // граница в пикселях - номер столбца / строки

        //получаем границы строк
    /*    RowBorder[] leftBordersArray = new RowBorder[CELLS_AMOUNT];
        for (int m = 0; m < CELLS_AMOUNT; m++) {
            leftBordersArray[m] = new RowBorder(cellsArray[m].getLeftBorder2(), m+1 );
        }
        //получаем границы из первого столбца, зная что индексы объектов в первом столбце - 0, 10, 20
        RowBorder[] topBordersArray = new RowBorder[CELLS_AMOUNT];
        for (int m = 0, n = 0; m < CELLS_AMOUNT; m++, n += CELLS_AMOUNT) {
            topBordersArray[m] = new RowBorder (cellsArray[n].getTopBorder2(), m+1);
        }*/

        switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN: // нажатие
                view.setBackgroundColor(getResources().getColor(R.color.teal_200));
                for (Cell item: cellsArray) { //вычисляем объект-ячейку по view
                    if (view == item.getCellId()) {
                        firstCell = item;
                        Log.d(TAG, "ACTION_DOWN" + view);
                        break;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE: // движение
                //получаем ячейку, в которой находится палец, по координатам
                Log.d(TAG, "ACTION_MOVE " + x + "," + y);
                for (int a = 0; a < cellBorderArray.length; a++) {
                    for (int b = 0; b < cellBorderArray[a].length; b++) {
                        if (cellBorderArray[a][b].getLeftBorder() < x
                                && cellBorderArray[a][b].getRightBorder() > x) {
                            curCellVer = cellBorderArray[a][b].getCoordVer();
                            break;
                        }
                    }
                }
                for (int a = 0; a < cellBorderArray.length; a++) {
                    for (int b = 0; b < cellBorderArray[a].length; b++) {
                        if (cellBorderArray[a][b].getTopBorder() < x
                                && cellBorderArray[a][b].getBottomBorder() > x) {
                            curCellHor = cellBorderArray[a][b].getCoordHor();
                            break;
                        }
                    }
                }
                Log.d(TAG, "ACTION_MOVE Cell " + curCellVer + "," + curCellHor);


            /*    for (CellBorder item: leftBordersArray) {
                    if (item.getBorder() + 30 > x && x > item.getBorder()) {
                        int moveHor = firstCell.getHor();
                        int moveVer = item.getCoord();
                        //получаем ячейку по cellId и окрашиваем ячейку
                        for (Cell cell: cellsArray) {
                            if (moveHor == cell.getHor() && moveVer == cell.getVer()) {
                                cell.getCellId().setBackgroundColor(getResources().getColor(R.color.teal_200));
                                break;
                            }
                        }
                    }
                }
                //перебираем все 10 значений строк + 5px
                for (CellBorder item: topBordersArray) {
                    if (item.getBorder() + 30 > y && y > item.getBorder()) {
                        int moveHor = item.getCoord();
                        int moveVer = firstCell.getVer();
                        //получаем ячейку по cellId и окрашиваем ячейку
                        for (Cell cell: cellsArray) {
                            if (moveHor == cell.getHor() && moveVer == cell.getVer()) {
                                cell.getCellId().setBackgroundColor(getResources().getColor(R.color.teal_200));
                                break;
                            }
                        }
                    }
                }*/
                break;
            case MotionEvent.ACTION_UP: // отпускание
            case MotionEvent.ACTION_CANCEL:
                Log.d(TAG, "ACTION_UP");
                break;
        }
        return true;
    }


   /* public float calculateTopBorder (int g, Arrays topBordersArray) {
        float topBorder = 0;
         for (RowBorder rowborder: topBordersArray) {//получаем верхнюю границу
             if (rowborder.getCoord() == g+1) {
                 topBorder = rowborder.getBorder();
                 break;
             }
         }
         return topBorder;
    }*/

            public float pleaseCalculateTopBorder (int v) {
                float topBorder = 0;
                for (Cell cell: cellsArray) {//получаем верхнюю границу
                    if (cell.getVer() == v + 1) {
                        topBorder = cell.getTopBorder2();
                        break;
                    }
                }
                return topBorder;
            }

            public float pleaseCalculateLeftBorder (int g) {
                float leftBorder = 0;
                for (Cell cell: cellsArray) {//получаем левую границу
                    if (cell.getHor() == g + 1) {
                        leftBorder = cell.getLeftBorder2();
                        break;
                    }
                }
                return leftBorder;
            }

            public float pleaseCalculateBottomBorder (int v) {
                float topBorder,
                        bottomBorder = 0;
                for (Cell cell: cellsArray) {//получаем верхнюю границу
                    if (cell.getVer() == v + 1) {
                        topBorder = cell.getTopBorder2();
                        bottomBorder = topBorder + cell.getCellId().getHeight();
                        break;
                    }
                }
                return bottomBorder;
            }

            public float pleaseCalculateRightBorder (int g) {
                float leftBorder,
                        rightBorder = 0;
                for (Cell cell: cellsArray) {//получаем верхнюю границу
                    if (cell.getVer() == g + 1) {
                        leftBorder = cell.getTopBorder2();
                        rightBorder = leftBorder + cell.getCellId().getWidth();
                        break;
                    }
                }
                return rightBorder;
            }
}