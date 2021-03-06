/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#ifndef BASIC_H
#define BASIC_H

#include <gmp.h>

typedef struct basic_ctx_t {
  mpz_t NSquared;
  mpz_t prod;
  mpz_t tmp;
  mpz_t tmp2;
} basic_ctx_t;

extern void basic_init(basic_ctx_t *ctx, mpz_t NSquared);
extern void basic_fini(basic_ctx_t *ctx);
extern void basic_insert_data_part2(basic_ctx_t *ctx, mpz_t queryElement, int part);
extern void basic_compute_column_and_clear_data(basic_ctx_t *ctx, mpz_t out);

#endif // BASIC_H

