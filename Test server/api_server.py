from flask import Flask, request, jsonify
import mysql.connector


app = Flask(__name__)

# Конфигурация подключения к БД
DB_CONFIG = {
    'host': 'localhost',
    'port': 3307,
    'user': 'gleb',
    'password': '12345',
    'database': 'restaurant'
}

def get_db_connection():
    """Устанавливает соединение с базой данных."""
    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        return conn
    except mysql.connector.Error as err:
        print(f"Ошибка подключения к БД: {err}")
        return None

@app.route('/api/orders/modifiable', methods=['GET'])
def get_modifiable_orders_api():
    try:
        conn = get_db_connection()
        if not conn: return jsonify({"error": "DB connection failed"}), 500
        cursor = conn.cursor(dictionary=True)
        sql = """
            SELECT 
                o.id, 
                o.table_number, 
                DATE_FORMAT(o.order_time, '%Y-%m-%d %H:%i') as order_time_formatted,
                o.status,
                GROUP_CONCAT(DISTINCT mi.name ORDER BY mi.name SEPARATOR ', ') as items_preview 
            FROM orders o
            LEFT JOIN order_items oi ON o.id = oi.order_id
            LEFT JOIN menu_items mi ON oi.menu_item_id = mi.id 
            WHERE o.status IN ('new', 'accepted') -- Пример статусов
            GROUP BY o.id, o.table_number, o.order_time, o.status
            ORDER BY o.order_time DESC
        """
        cursor.execute(sql)
        orders_from_db = cursor.fetchall()
        formatted_orders = []
        for order_db in orders_from_db:
            description = f"Заказ #{order_db['id']} (Стол {order_db['table_number']}, {order_db['order_time_formatted']}, Статус: {order_db['status']})"
            if order_db['items_preview']:
                description += f" - {order_db['items_preview'][:50]}"
                if len(order_db['items_preview']) > 50: description += "..."
            formatted_orders.append({
                "id": order_db['id'],
                "description": description,
                "status": order_db['status'] # Передаем статус
            })
        cursor.close()
        conn.close()
        return jsonify(formatted_orders), 200
    except Exception as e:
        # ... (обработка ошибок) ...
        return jsonify({"error": f"Ошибка получения модифицируемых заказов: {str(e)}"}), 500


@app.route('/api/order/<int:order_id>', methods=['GET'])
def get_order_details_api(order_id):
    try:
        conn = get_db_connection()
        if not conn: return jsonify({"error": "DB connection failed"}), 500
        cursor = conn.cursor(dictionary=True)

        cursor.execute("""
            SELECT o.id, o.table_number, o.status,
                   DATE_FORMAT(o.order_time, '%Y-%m-%d %H:%i') as order_time_formatted,
                   (SELECT SUM(mi.price * oi.quantity) 
                    FROM order_items oi 
                    JOIN menu_items mi ON oi.menu_item_id = mi.id 
                    WHERE oi.order_id = o.id) as total_amount
            FROM orders o
            WHERE o.id = %s
        """, (order_id,))
        order_details = cursor.fetchone()

        if not order_details:
            return jsonify({"error": "Заказ не найден"}), 404

        cursor.execute("""
            SELECT oi.menu_item_id, mi.name as menu_item_name, oi.quantity, mi.price 
            FROM order_items oi
            JOIN menu_items mi ON oi.menu_item_id = mi.id
            WHERE oi.order_id = %s
        """, (order_id,))
        items = cursor.fetchall()

        order_details['items'] = items
        if order_details.get('total_amount') is None and not items:
            order_details['total_amount'] = 0.00
        elif order_details.get(
                'total_amount') is None and items:
            calculated_sum = sum(item['price'] * item['quantity'] for item in items)
            order_details['total_amount'] = calculated_sum

        cursor.close()
        conn.close()
        return jsonify(order_details), 200
    except Exception as e:
        print(f"Ошибка получения деталей заказа с суммой: {e}")
        return jsonify({"error": f"Ошибка получения деталей заказа: {str(e)}"}), 500


@app.route('/api/order/<int:order_id>', methods=['PUT'])
def update_order_api(order_id):
    try:
        data = request.get_json()
        if not data or 'items' not in data or not isinstance(data['items'], list):
            return jsonify({"error": "Необходим массив 'items' для обновления"}), 400

        new_order_items_data = data['items']

        conn = get_db_connection()
        if not conn: return jsonify({"error": "DB connection failed"}), 500
        cursor = conn.cursor()


        cursor.execute("SELECT status FROM orders WHERE id = %s", (order_id,))
        current_order = cursor.fetchone()
        if not current_order:
            return jsonify({"error": "Заказ не найден"}), 404
        current_status = current_order[0]
        if current_status not in ['new', 'accepted']:  # Пример статусов
            cursor.close()
            conn.close()
            return jsonify({"error": f"Заказ со статусом '{current_status}' не может быть изменен"}), 403

        try:
            # 1. Удалить старые позиции заказа
            cursor.execute("DELETE FROM order_items WHERE order_id = %s", (order_id,))

            # 2. Вставить новые позиции заказа (если они есть)
            if new_order_items_data:
                item_sql = "INSERT INTO order_items (order_id, menu_item_id, quantity) VALUES (%s, %s, %s)"
                items_to_insert = []
                for item_data in new_order_items_data:
                    if 'itemId' not in item_data or 'qty' not in item_data:
                        raise ValueError("Элемент в 'items' должен содержать 'itemId' и 'qty'")
                    item_id = int(item_data['itemId'])
                    quantity = int(item_data['qty'])
                    if quantity <= 0:
                        continue
                    items_to_insert.append((order_id, item_id, quantity))

                if items_to_insert:
                    cursor.executemany(item_sql, items_to_insert)

            conn.commit()
            return jsonify({"message": f"Заказ #{order_id} успешно обновлен"}), 200

        except (ValueError, Exception) as e_inner:
            if conn.is_connected(): conn.rollback()
            print(f"Ошибка при обновлении заказа (внутренняя): {e_inner}")
            return jsonify({"error": f"Ошибка при обновлении заказа: {str(e_inner)}"}), 500
        finally:
            if cursor: cursor.close()
            if conn.is_connected(): conn.close()

    except Exception as e_outer:
        print(f"Ошибка при обновлении заказа (внешняя): {e_outer}")
        return jsonify({"error": f"Внутренняя ошибка сервера: {str(e_outer)}"}), 500


# Эндпоинт для отмены заказа
@app.route('/api/order/<int:order_id>/cancel', methods=['POST'])
def cancel_order_api(order_id):
    try:
        conn = get_db_connection()
        if not conn: return jsonify({"error": "DB connection failed"}), 500
        cursor = conn.cursor()


        cursor.execute("SELECT status FROM orders WHERE id = %s", (order_id,))
        current_order = cursor.fetchone()  # Без dictionary=True
        if not current_order:
            return jsonify({"error": "Заказ не найден"}), 404

        current_status = current_order[0]
        if current_status not in ['new', 'accepted', 'preparing']:
            cursor.close()
            conn.close()
            return jsonify({"error": f"Заказ со статусом '{current_status}' не может быть отменен"}), 403

        cursor.execute("UPDATE orders SET status = 'cancelled' WHERE id = %s", (order_id,))
        conn.commit()


        cursor.close()
        conn.close()
        return jsonify({"message": f"Заказ #{order_id} успешно отменен"}), 200
    except Exception as e:
        return jsonify({"error": f"Ошибка при отмене заказа: {str(e)}"}), 500

@app.route('/api/menu_items', methods=['GET'])
def get_menu_items_api():
    """
    Эндпоинт для получения списка блюд.
    Может принимать параметр category_id для фильтрации.
    Возвращает также category_id и category_name для каждого блюда.
    """
    try:
        category_id_filter = request.args.get('category_id', type=int)

        conn = get_db_connection()
        if not conn:
            return jsonify({"error": "Не удалось подключиться к базе данных"}), 500

        cursor = conn.cursor(dictionary=True)


        sql = """
            SELECT 
                mi.id, 
                mi.name, 
                mi.price, 
                mi.category_id, 
                mc.name as category_name 
            FROM menu_items mi
            LEFT JOIN menu_categories mc ON mi.category_id = mc.id
        """
        params = []
        if category_id_filter is not None:
            sql += " WHERE mi.category_id = %s"
            params.append(category_id_filter)

        sql += " ORDER BY mc.sort_order ASC, mc.name ASC, mi.name ASC"

        cursor.execute(sql, tuple(params))
        menu_items = cursor.fetchall()

        cursor.close()
        conn.close()
        return jsonify(menu_items), 200

    except Exception as e:
        print(f"Ошибка при получении списка блюд: {e}")
        return jsonify({"error": f"Внутренняя ошибка сервера при получении блюд: {str(e)}"}), 500


@app.route('/api/order', methods=['POST'])
def send_order_api():
    """
    Эндпоинт для создания нового заказа с несколькими позициями.
    Ожидает JSON: {"table": "стол_номер", "items": [{"itemId": id, "qty": количество}, ...]}
    """
    try:
        data = request.get_json()
        if not data or 'table' not in data or 'items' not in data or not isinstance(data['items'], list):
            return jsonify({"error": "Некорректные данные. Нужны 'table' и массив 'items'"}), 400

        table_number = str(data['table'])
        order_items_data = data['items']

        if not order_items_data:  # Если массив items пуст
            return jsonify({"error": "Список блюд в заказе не может быть пустым"}), 400

        conn = get_db_connection()
        if not conn:
            return jsonify({"error": "Не удалось подключиться к базе данных"}), 500

        cursor = conn.cursor()
        order_id = None

        try:
            # 1. Вставить в таблицу orders
            order_sql = "INSERT INTO orders (table_number, order_time) VALUES (%s, NOW())"
            cursor.execute(order_sql, (table_number,))
            order_id = cursor.lastrowid

            if not order_id:
                raise Exception("Не удалось создать запись в таблице orders")

            # 2. Вставить все позиции заказа в таблицу order_items
            item_sql = "INSERT INTO order_items (order_id, menu_item_id, quantity) VALUES (%s, %s, %s)"

            items_to_insert = []
            for item_data in order_items_data:
                if 'itemId' not in item_data or 'qty' not in item_data:
                    raise ValueError("Каждый элемент в 'items' должен содержать 'itemId' и 'qty'")
                item_id = int(item_data['itemId'])
                quantity = int(item_data['qty'])
                if quantity <= 0:
                    raise ValueError("Количество должно быть положительным числом")
                items_to_insert.append((order_id, item_id, quantity))

            if not items_to_insert:
                raise ValueError("Нет позиций для добавления в заказ")

            cursor.executemany(item_sql, items_to_insert)

            conn.commit()
            message = f"Заказ #{order_id} успешно создан с {len(items_to_insert)} позициями."
            return jsonify({"message": message, "orderId": order_id}), 201

        except (ValueError, Exception) as e_inner:
            if conn.is_connected():
                conn.rollback()
            print(f"Ошибка при обработке заказа (внутренняя): {e_inner}")
            return jsonify({"error": f"Ошибка при обработке заказа: {str(e_inner)}"}), 500
        finally:
            if cursor:
                cursor.close()
            if conn.is_connected():
                conn.close()

    except Exception as e_outer:
        print(f"Ошибка при обработке заказа (внешняя): {e_outer}")
        return jsonify({"error": f"Внутренняя ошибка сервера: {str(e_outer)}"}), 500


@app.route('/api/register', methods=['POST'])
def register_user_api():
    """
    Эндпоинт для регистрации нового пользователя.
    Ожидает JSON: {"username": "имя_пользователя", "password": "пароль"}
    """
    try:
        data = request.get_json()
        if not data or 'username' not in data or 'password' not in data:
            return jsonify({"error": "Необходимы 'username' и 'password'"}), 400

        username = str(data['username']).strip()
        password = str(data['password'])

        if not username or not password:
            return jsonify({"error": "Имя пользователя и пароль не могут быть пустыми"}), 400

        if len(password) < 4:  # Простая проверка длины пароля
            return jsonify({"error": "Пароль должен быть не менее 4 символов"}), 400

        conn = get_db_connection()
        if not conn:
            return jsonify({"error": "Не удалось подключиться к базе данных"}), 500

        cursor = conn.cursor()

        # Проверка, существует ли уже такой пользователь
        cursor.execute("SELECT id FROM app_users WHERE username = %s", (username,))
        if cursor.fetchone():
            cursor.close()
            conn.close()
            return jsonify({"error": "Пользователь с таким именем уже существует"}), 409  # 409 Conflict


        insert_sql = "INSERT INTO app_users (username, password) VALUES (%s, %s)"
        cursor.execute(insert_sql, (username, password))
        conn.commit()

        user_id = cursor.lastrowid
        cursor.close()
        conn.close()

        return jsonify(
            {"message": "Пользователь успешно зарегистрирован", "userId": user_id, "username": username}), 201

    except mysql.connector.Error as db_err:
        print(f"Ошибка БД при регистрации: {db_err}")
        # Закрываем соединение, если оно еще открыто и была ошибка БД
        if 'conn' in locals() and conn.is_connected():
            if 'cursor' in locals() and cursor:
                cursor.close()
            conn.close()
        return jsonify({"error": f"Ошибка базы данных: {str(db_err)}"}), 500
    except Exception as e:
        print(f"Ошибка при регистрации: {e}")
        return jsonify({"error": f"Внутренняя ошибка сервера: {str(e)}"}), 500


@app.route('/api/login', methods=['POST'])
def login_user_api():
    """
    Эндпоинт для входа пользователя.
    Ожидает JSON: {"username": "имя_пользователя", "password": "пароль"}
    """
    try:
        data = request.get_json()
        if not data or 'username' not in data or 'password' not in data:
            return jsonify({"error": "Необходимы 'username' и 'password'"}), 400

        username = str(data['username']).strip()
        password = str(data['password'])  # Пароль приходит в открытом виде

        conn = get_db_connection()
        if not conn:
            return jsonify({"error": "Не удалось подключиться к базе данных"}), 500

        cursor = conn.cursor(dictionary=True)


        cursor.execute("SELECT id, username, password FROM app_users WHERE username = %s", (username,))
        user = cursor.fetchone()

        cursor.close()
        conn.close()

        if user and user['password'] == password:
            # Пароль совпал
            return jsonify(
                {"message": "Вход выполнен успешно", "userId": user['id'], "username": user['username']}), 200
        else:
            return jsonify({"error": "Неверное имя пользователя или пароль"}), 401

    except mysql.connector.Error as db_err:
        print(f"Ошибка БД при входе: {db_err}")
        if 'conn' in locals() and conn.is_connected():
            if 'cursor' in locals() and cursor:
                cursor.close()
            conn.close()
        return jsonify({"error": f"Ошибка базы данных: {str(db_err)}"}), 500
    except Exception as e:
        print(f"Ошибка при входе: {e}")
        return jsonify({"error": f"Внутренняя ошибка сервера: {str(e)}"}), 500


@app.route('/api/orders/new', methods=['GET'])
def get_new_orders_api():
    """
    Эндпоинт для получения новых заказов с детализацией.
    Возвращает список заказов, каждый из которых содержит ID, номер стола и список позиций (название блюда, количество).
    """
    try:
        conn = get_db_connection()
        if not conn:
            return jsonify({"error": "Не удалось подключиться к базе данных"}), 500

        cursor = conn.cursor(dictionary=True)


        sql = """
            SELECT 
                o.id AS order_id, 
                o.table_number,
                o.order_time,
                mi.name AS menu_item_name, 
                oi.quantity
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            JOIN menu_items mi ON oi.menu_item_id = mi.id
            WHERE o.status = 'new'  -- Или другой статус, который ты считаешь "новым"
            ORDER BY o.id ASC, mi.name ASC; 
        """


        cursor.execute(sql)
        rows = cursor.fetchall()
        cursor.close()
        conn.close()


        orders_dict = {}
        for row in rows:
            order_id = row['order_id']
            if order_id not in orders_dict:
                orders_dict[order_id] = {
                    "orderId": order_id,
                    "tableNumber": row['table_number'],
                    "orderTime": row['order_time'].strftime('%Y-%m-%d %H:%M:%S') if row['order_time'] else None,
                    # Форматируем время
                    "items": []
                }
            orders_dict[order_id]['items'].append({
                "menuItemName": row['menu_item_name'],
                "quantity": row['quantity']
            })


        formatted_orders = list(orders_dict.values())

        return jsonify(formatted_orders), 200

    except Exception as e:
        print(f"Ошибка при получении новых заказов: {e}")
        if 'conn' in locals() and conn.is_connected():
            if 'cursor' in locals() and cursor:
                cursor.close()
            conn.close()
        return jsonify({"error": f"Внутренняя ошибка сервера: {str(e)}"}), 500


@app.route('/api/review', methods=['POST'])
def leave_review_api():
    try:
        data = request.get_json()
        if not data or 'order_id' not in data or 'rating' not in data or 'comment' not in data:

            return jsonify({"error": "Необходимы 'order_id', 'rating' и 'comment'"}), 400

        order_id = int(data['order_id'])
        rating = int(data['rating'])
        comment = str(data['comment']).strip()
        reviewer_name = data.get('reviewer_name', None)
        if reviewer_name:
            reviewer_name = reviewer_name.strip()
            if not reviewer_name:
                reviewer_name = None

        user_id_for_fk = data.get('user_id')  # ID залогиненного пользователя (официанта)


        if not (1 <= rating <= 5):
            return jsonify({"error": "Оценка должна быть от 1 до 5"}), 400
        if not comment:
            return jsonify({"error": "Текст отзыва не может быть пустым"}), 400


        conn = get_db_connection()
        if not conn:
            return jsonify({"error": "Не удалось подключиться к базе данных"}), 500

        cursor = conn.cursor()



        insert_sql = """
            INSERT INTO reviews (order_id, user_id, rating, comment, reviewer_name, review_time) 
            VALUES (%s, %s, %s, %s, %s, NOW()) 
        """

        cursor.execute(insert_sql, (order_id, user_id_for_fk, rating, comment, reviewer_name))
        conn.commit()

        review_id = cursor.lastrowid
        cursor.close()
        conn.close()

        return jsonify({"message": "Отзыв успешно добавлен", "reviewId": review_id}), 201

    except ValueError:
        return jsonify({"error": "Неверный формат order_id или rating."}), 400
    except mysql.connector.Error as db_err:
        print(f"Ошибка БД при добавлении отзыва: {db_err}")
        if 'conn' in locals() and conn.is_connected():
            if 'cursor' in locals() and cursor:
                cursor.close()
            conn.close()
        return jsonify({"error": f"Ошибка базы данных: {str(db_err)}"}), 500
    except Exception as e:
        print(f"Ошибка при добавлении отзыва: {e}")
        return jsonify({"error": f"Внутренняя ошибка сервера: {str(e)}"}), 500

@app.route('/api/orders_for_review', methods=['GET'])
def get_orders_for_review_api():
    """Эндпоинт для получения списка заказов, для которых можно оставить отзыв,
       ИСКЛЮЧАЯ заказы со статусом 'cancelled'."""
    try:
        conn = get_db_connection()
        if not conn:
            return jsonify({"error": "Не удалось подключиться к базе данных"}), 500

        cursor = conn.cursor(dictionary=True)
        sql = """
            SELECT 
                o.id, 
                o.table_number, 
                DATE_FORMAT(o.order_time, '%Y-%m-%d %H:%i') as order_time_formatted,
                o.status -- Можно вернуть статус, если он нужен клиенту для отображения
            FROM orders o
            WHERE o.status != 'cancelled'  -- <--- ОСНОВНОЕ ИЗМЕНЕНИЕ
            -- Если ты хочешь отзывы только на определенные статусы, ИСКЛЮЧАЯ cancelled:
            -- WHERE o.status IN ('new', 'accepted', 'preparing', 'served', 'paid') 
            -- ИЛИ, если хочешь все, кроме отмененных и, например, еще не оплаченных:
            -- WHERE o.status NOT IN ('cancelled', 'pending_payment') 
            ORDER BY o.order_time DESC 
        """
        cursor.execute(sql)
        orders_from_db = cursor.fetchall()

        formatted_orders = []
        for order_db in orders_from_db:

            description = f"Заказ #{order_db['id']} (Стол {order_db['table_number']}, {order_db['order_time_formatted']})"
            if order_db.get('status'):
                 description += f" - Статус: {order_db['status']}"

            formatted_orders.append({
                "id": order_db['id'],
                "description": description
            })

        cursor.close()
        conn.close()
        return jsonify(formatted_orders), 200

    except Exception as e:
        print(f"Ошибка при получении заказов для отзыва (исключая отмененные): {e}")
        return jsonify({"error": f"Внутренняя ошибка сервера при получении заказов: {str(e)}"}), 500


@app.route('/api/orders/ready_for_payment', methods=['GET'])
def get_orders_for_payment_api():
    """
    Эндпоинт для получения списка заказов, готовых к оплате.
    """
    try:
        conn = get_db_connection()
        if not conn: return jsonify({"error": "DB connection failed"}), 500
        cursor = conn.cursor(dictionary=True)

        # Выбираем заказы, готовые к оплате
        sql = """
            SELECT 
                o.id, 
                o.table_number, 
                DATE_FORMAT(o.order_time, '%Y-%m-%d %H:%i') as order_time_formatted,
                o.status,
                (SELECT SUM(mi.price * oi.quantity) 
                 FROM order_items oi 
                 JOIN menu_items mi ON oi.menu_item_id = mi.id 
                 WHERE oi.order_id = o.id) as total_amount  -- Подсчет суммы заказа
            FROM orders o
            WHERE o.status IN ('served', 'bill_requested') -- Пример статусов для оплаты
            ORDER BY o.order_time ASC 
        """


        cursor.execute(sql)
        orders_from_db = cursor.fetchall()

        formatted_orders = []
        for order_db in orders_from_db:
            description = f"Заказ #{order_db['id']} (Стол {order_db['table_number']}, {order_db['order_time_formatted']})"
            total_amount_val = order_db.get('total_amount')
            if total_amount_val is not None:
                description += f" - Сумма: {total_amount_val:.2f}"  # Форматируем до 2 знаков после запятой
            else:
                description += " - Сумма: (не рассчитана)"

            formatted_orders.append({
                "id": order_db['id'],
                "description": description,
                "status": order_db.get('status'),
                "total_amount": total_amount_val  # Передаем сумму, если она рассчитана
            })

        cursor.close()
        conn.close()
        return jsonify(formatted_orders), 200
    except Exception as e:
        print(f"Ошибка получения заказов к оплате: {e}")
        return jsonify({"error": f"Внутренняя ошибка сервера: {str(e)}"}), 500


@app.route('/api/order/<int:order_id>/pay', methods=['POST'])
def pay_order_api(order_id):
    try:

        conn = get_db_connection()
        if not conn: return jsonify({"error": "DB connection failed"}), 500
        cursor = conn.cursor()

        cursor.execute("SELECT status FROM orders WHERE id = %s", (order_id,))
        current_order = cursor.fetchone()
        if not current_order: return jsonify({"error": "Заказ не найден"}), 404


        cursor.execute("UPDATE orders SET status = 'paid' WHERE id = %s", (order_id,))
        conn.commit()

        cursor.close()
        conn.close()
        return jsonify({"message": f"Заказ #{order_id} успешно оплачен и закрыт"}), 200
    except Exception as e:
        print(f"Ошибка при оплате заказа: {e}")
        return jsonify({"error": f"Ошибка при оплате заказа: {str(e)}"}), 500

@app.route('/api/report', methods=['GET'])
def get_report_api():
    """Эндпоинт для получения отчета."""
    try:
        conn = get_db_connection()
        if not conn:
            return jsonify({"error": "Не удалось подключиться к базе данных"}), 500

        cursor = conn.cursor(dictionary=True)
        sql = """
            SELECT COUNT(DISTINCT o.id) AS total_orders, SUM(oi.quantity) AS total_items
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
        """
        cursor.execute(sql)
        report_data = cursor.fetchone()

        cursor.close()
        conn.close()

        if report_data:

            total_items = report_data['total_items'] if report_data['total_items'] is not None else 0
            response_data = {
                "totalOrders": report_data['total_orders'],
                "totalItems": total_items

            }
            return jsonify(response_data), 200
        else:
            return jsonify({"totalOrders": 0, "totalItems": 0}), 200

    except Exception as e:
        print(f"Ошибка при получении отчета: {e}")
        if 'conn' in locals() and conn.is_connected():
            if 'cursor' in locals() and cursor:
                cursor.close()
            conn.close()
        return jsonify({"error": f"Внутренняя ошибка сервера: {str(e)}"}), 500

if __name__ == '__main__':

    app.run(host='0.0.0.0', port=5000, debug=True)